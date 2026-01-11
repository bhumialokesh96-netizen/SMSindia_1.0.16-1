-- ============================================
-- SMS INDIA DATABASE SCHEMA - RPC FUNCTIONS
-- ============================================
-- Version: 1.0
-- Description: Remote Procedure Calls for batch processing and business logic
-- Execute this file after 01_tables.sql
-- ============================================

-- ============================================
-- 1. FETCH BATCH TASKS
-- ============================================
-- Fetches and locks a batch of SMS tasks for a user
-- Returns up to batch_size pending tasks and marks them as assigned

CREATE OR REPLACE FUNCTION public.fetch_batch_tasks(
    p_user_id UUID,
    p_batch_size INTEGER DEFAULT 10
)
RETURNS TABLE (
    id UUID,
    recipient VARCHAR,
    message TEXT,
    reward DECIMAL,
    created_at TIMESTAMPTZ
)
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_task_ids UUID[];
    v_batch_id UUID;
BEGIN
    -- 1. Find available pending tasks
    SELECT ARRAY_AGG(t.id)
    INTO v_task_ids
    FROM (
        SELECT id
        FROM public.sms_tasks
        WHERE status = 'pending'
        ORDER BY priority DESC, created_at ASC
        LIMIT p_batch_size
        FOR UPDATE SKIP LOCKED -- Prevents race conditions
    ) t;
    
    -- 2. Return empty if no tasks available
    IF v_task_ids IS NULL OR array_length(v_task_ids, 1) = 0 THEN
        RETURN;
    END IF;
    
    -- 3. Update tasks to assigned status
    UPDATE public.sms_tasks
    SET 
        status = 'assigned',
        assigned_to = p_user_id,
        assigned_at = NOW()
    WHERE id = ANY(v_task_ids);
    
    -- 4. Create batch record
    INSERT INTO public.batch_tasks (user_id, task_ids, status)
    VALUES (p_user_id, v_task_ids, 'in_progress')
    RETURNING id INTO v_batch_id;
    
    -- 5. Return task details
    RETURN QUERY
    SELECT 
        t.id,
        t.recipient,
        t.message,
        t.reward,
        t.created_at
    FROM public.sms_tasks t
    WHERE t.id = ANY(v_task_ids)
    ORDER BY t.priority DESC, t.created_at ASC;
END;
$$;

COMMENT ON FUNCTION public.fetch_batch_tasks IS 'Atomically fetches and locks SMS tasks for batch processing';

-- ============================================
-- 2. SUBMIT BATCH RESULTS
-- ============================================
-- Processes batch results, updates task statuses, and credits rewards

CREATE OR REPLACE FUNCTION public.submit_batch_results(
    p_user_id UUID,
    p_success_ids UUID[],
    p_fail_ids UUID[]
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_success_count INTEGER;
    v_fail_count INTEGER;
    v_total_reward DECIMAL(10, 2);
    v_task RECORD;
    v_batch_id UUID;
BEGIN
    v_success_count := COALESCE(array_length(p_success_ids, 1), 0);
    v_fail_count := COALESCE(array_length(p_fail_ids, 1), 0);
    v_total_reward := 0.00;
    
    -- 1. Process successful tasks
    IF v_success_count > 0 THEN
        -- Update tasks to completed
        UPDATE public.sms_tasks
        SET 
            status = 'completed',
            completed_at = NOW()
        WHERE id = ANY(p_success_ids)
            AND assigned_to = p_user_id;
        
        -- Calculate total reward
        SELECT COALESCE(SUM(reward), 0.00)
        INTO v_total_reward
        FROM public.sms_tasks
        WHERE id = ANY(p_success_ids)
            AND assigned_to = p_user_id;
        
        -- Create SMS logs
        FOR v_task IN 
            SELECT id, recipient, message, reward
            FROM public.sms_tasks
            WHERE id = ANY(p_success_ids)
                AND assigned_to = p_user_id
        LOOP
            INSERT INTO public.sms_logs (user_id, task_id, recipient, message, status, reward)
            VALUES (p_user_id, v_task.id, v_task.recipient, v_task.message, 'sent', v_task.reward);
        END LOOP;
        
        -- Update user balance and stats
        UPDATE public.users
        SET 
            balance = balance + v_total_reward,
            today_income = today_income + v_total_reward,
            total_income = total_income + v_total_reward,
            sms_count = sms_count + v_success_count,
            updated_at = NOW()
        WHERE id = p_user_id;
        
        -- Create transaction record
        INSERT INTO public.transactions (user_id, type, amount, description)
        VALUES (
            p_user_id, 
            'earning', 
            v_total_reward, 
            FORMAT('Completed %s SMS tasks', v_success_count)
        );
    END IF;
    
    -- 2. Process failed tasks
    IF v_fail_count > 0 THEN
        -- Update tasks to failed (they can be retried)
        UPDATE public.sms_tasks
        SET 
            status = 'pending', -- Reset to pending for retry
            assigned_to = NULL,
            assigned_at = NULL,
            retry_count = retry_count + 1
        WHERE id = ANY(p_fail_ids)
            AND assigned_to = p_user_id;
    END IF;
    
    -- 3. Find and update batch record
    SELECT id INTO v_batch_id
    FROM public.batch_tasks
    WHERE user_id = p_user_id
        AND status = 'in_progress'
        AND task_ids && (p_success_ids || p_fail_ids) -- Overlapping arrays
    ORDER BY created_at DESC
    LIMIT 1;
    
    IF v_batch_id IS NOT NULL THEN
        UPDATE public.batch_tasks
        SET 
            status = 'completed',
            success_count = v_success_count,
            fail_count = v_fail_count,
            total_reward = v_total_reward,
            completed_at = NOW()
        WHERE id = v_batch_id;
    END IF;
    
    -- 4. Return result summary
    RETURN jsonb_build_object(
        'success', TRUE,
        'success_count', v_success_count,
        'fail_count', v_fail_count,
        'reward_earned', v_total_reward,
        'message', FORMAT('Processed %s tasks successfully', v_success_count)
    );
    
EXCEPTION
    WHEN OTHERS THEN
        RETURN jsonb_build_object(
            'success', FALSE,
            'error', SQLERRM,
            'message', 'Failed to process batch results'
        );
END;
$$;

COMMENT ON FUNCTION public.submit_batch_results IS 'Processes batch task results and credits rewards';

-- ============================================
-- 3. CLAIM DAILY CHECK-IN
-- ============================================
-- Handles daily check-in rewards and streak tracking

CREATE OR REPLACE FUNCTION public.claim_daily_checkin(
    p_user_id UUID
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_last_checkin DATE;
    v_current_streak INTEGER;
    v_reward DECIMAL(10, 2);
    v_bonus_spins INTEGER := 0;
BEGIN
    -- Get user's last check-in date and streak
    SELECT last_checkin_date, streak
    INTO v_last_checkin, v_current_streak
    FROM public.users
    WHERE id = p_user_id;
    
    -- Check if already claimed today
    IF v_last_checkin = CURRENT_DATE THEN
        RETURN jsonb_build_object(
            'success', FALSE,
            'message', 'Already claimed today. Come back tomorrow!'
        );
    END IF;
    
    -- Calculate streak
    IF v_last_checkin = CURRENT_DATE - INTERVAL '1 day' THEN
        -- Consecutive day, increment streak
        v_current_streak := v_current_streak + 1;
    ELSE
        -- Streak broken, reset to 1
        v_current_streak := 1;
    END IF;
    
    -- Calculate reward based on streak (₹1 base + ₹0.50 per streak day, max ₹10)
    v_reward := LEAST(1.00 + (v_current_streak - 1) * 0.50, 10.00);
    
    -- Bonus spins every 7 days
    IF v_current_streak % 7 = 0 THEN
        v_bonus_spins := 3;
    END IF;
    
    -- Update user
    UPDATE public.users
    SET 
        balance = balance + v_reward,
        today_income = today_income + v_reward,
        total_income = total_income + v_reward,
        spins = spins + v_bonus_spins,
        streak = v_current_streak,
        last_checkin_date = CURRENT_DATE,
        updated_at = NOW()
    WHERE id = p_user_id;
    
    -- Create transaction
    INSERT INTO public.transactions (user_id, type, amount, description)
    VALUES (
        p_user_id, 
        'daily_checkin', 
        v_reward, 
        FORMAT('Day %s check-in reward', v_current_streak)
    );
    
    RETURN jsonb_build_object(
        'success', TRUE,
        'reward', v_reward,
        'streak', v_current_streak,
        'bonus_spins', v_bonus_spins,
        'message', FORMAT('Claimed ₹%.2f! Streak: %s days', v_reward, v_current_streak)
    );
END;
$$;

COMMENT ON FUNCTION public.claim_daily_checkin IS 'Claims daily check-in reward with streak bonus';

-- ============================================
-- 4. WATCH AD REWARD
-- ============================================
-- Credits reward for watching an advertisement

CREATE OR REPLACE FUNCTION public.watch_ad_reward(
    p_user_id UUID,
    p_ad_type VARCHAR DEFAULT 'rewarded'
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_reward DECIMAL(10, 2) := 0.50; -- ₹0.50 per ad
    v_ad_progress INTEGER;
    v_bonus_reward DECIMAL(10, 2) := 0;
BEGIN
    -- Get current ad progress
    SELECT ad_progress INTO v_ad_progress
    FROM public.users
    WHERE id = p_user_id;
    
    -- Increment progress
    v_ad_progress := (v_ad_progress + 1) % 10;
    
    -- Every 10th ad gets bonus
    IF v_ad_progress = 0 THEN
        v_bonus_reward := 5.00; -- ₹5 bonus
        v_reward := v_reward + v_bonus_reward;
    END IF;
    
    -- Update user balance
    UPDATE public.users
    SET 
        balance = balance + v_reward,
        today_income = today_income + v_reward,
        total_income = total_income + v_reward,
        ad_progress = v_ad_progress,
        updated_at = NOW()
    WHERE id = p_user_id;
    
    -- Create transaction
    INSERT INTO public.transactions (user_id, type, amount, description, metadata)
    VALUES (
        p_user_id,
        'ad_reward',
        v_reward,
        FORMAT('Watched %s ad', p_ad_type),
        jsonb_build_object('ad_type', p_ad_type, 'bonus', v_bonus_reward > 0)
    );
    
    RETURN jsonb_build_object(
        'success', TRUE,
        'reward', v_reward,
        'bonus', v_bonus_reward,
        'progress', FORMAT('%s/10', (v_ad_progress + 9) % 10 + 1),
        'message', CASE 
            WHEN v_bonus_reward > 0 THEN FORMAT('Bonus! Earned ₹%.2f', v_reward)
            ELSE FORMAT('Earned ₹%.2f', v_reward)
        END
    );
END;
$$;

COMMENT ON FUNCTION public.watch_ad_reward IS 'Credits reward for watching advertisements';

-- ============================================
-- GRANT EXECUTE PERMISSIONS
-- ============================================
GRANT EXECUTE ON FUNCTION public.fetch_batch_tasks TO anon, authenticated;
GRANT EXECUTE ON FUNCTION public.submit_batch_results TO anon, authenticated;
GRANT EXECUTE ON FUNCTION public.claim_daily_checkin TO anon, authenticated;
GRANT EXECUTE ON FUNCTION public.watch_ad_reward TO anon, authenticated;

-- ============================================
-- SETUP COMPLETE
-- ============================================
-- Next: Execute 03_triggers.sql
