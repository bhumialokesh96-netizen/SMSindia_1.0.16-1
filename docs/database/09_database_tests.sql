-- ============================================
-- DATABASE TESTS AND VALIDATION
-- ============================================
-- Version: 1.0
-- Description: Comprehensive tests for triggers, RLS, RPC functions, and performance
-- Execute after all schema files including 08_database_fixes_and_optimizations.sql
-- ============================================

-- ============================================
-- TEST SETUP
-- ============================================

-- Create test data cleanup function
CREATE OR REPLACE FUNCTION cleanup_test_data()
RETURNS void AS $$
BEGIN
    DELETE FROM public.sms_logs WHERE user_id IN (
        SELECT id FROM public.users WHERE phone LIKE 'TEST%'
    );
    DELETE FROM public.transactions WHERE user_id IN (
        SELECT id FROM public.users WHERE phone LIKE 'TEST%'
    );
    DELETE FROM public.batch_tasks WHERE user_id IN (
        SELECT id FROM public.users WHERE phone LIKE 'TEST%'
    );
    DELETE FROM public.withdrawals WHERE user_id IN (
        SELECT id FROM public.users WHERE phone LIKE 'TEST%'
    );
    DELETE FROM public.referral_transactions WHERE referrer_id IN (
        SELECT id FROM public.users WHERE phone LIKE 'TEST%'
    );
    DELETE FROM public.referral_analytics WHERE user_id IN (
        SELECT id FROM public.users WHERE phone LIKE 'TEST%'
    );
    DELETE FROM public.sms_tasks WHERE recipient LIKE 'TEST%';
    DELETE FROM public.users WHERE phone LIKE 'TEST%';
    
    RAISE NOTICE 'Test data cleaned up successfully';
END;
$$ LANGUAGE plpgsql;

-- ============================================
-- PART 1: TRIGGER TESTS
-- ============================================

DO $$
DECLARE
    v_user_id UUID;
    v_task_ids UUID[];
    v_batch_id UUID;
    v_initial_balance DECIMAL(10, 2);
    v_final_balance DECIMAL(10, 2);
BEGIN
    RAISE NOTICE '=================================================';
    RAISE NOTICE 'TEST 1: Update Timestamp Trigger';
    RAISE NOTICE '=================================================';
    
    -- Clean up any previous test data
    PERFORM cleanup_test_data();
    
    -- Create test user
    INSERT INTO public.users (phone, email, password, device_id, balance)
    VALUES ('TEST1234567890', 'test1@test.com', 'test123', 'device-test-1', 100.00)
    RETURNING id INTO v_user_id;
    
    -- Get initial timestamp
    SELECT updated_at INTO v_initial_balance
    FROM public.users WHERE id = v_user_id;
    
    -- Sleep for 1 second
    PERFORM pg_sleep(1);
    
    -- Update user
    UPDATE public.users SET balance = 150.00 WHERE id = v_user_id;
    
    -- Check if updated_at changed
    SELECT updated_at INTO v_final_balance
    FROM public.users WHERE id = v_user_id;
    
    IF v_final_balance > v_initial_balance THEN
        RAISE NOTICE '✓ Update timestamp trigger working correctly';
    ELSE
        RAISE WARNING '✗ Update timestamp trigger NOT working';
    END IF;
    
    RAISE NOTICE '=================================================';
    RAISE NOTICE 'TEST 2: Batch Expiration Trigger';
    RAISE NOTICE '=================================================';
    
    -- Create test SMS tasks
    INSERT INTO public.sms_tasks (recipient, message, reward, status)
    SELECT 
        'TEST' || generate_series,
        'Test message ' || generate_series,
        1.00,
        'pending'
    FROM generate_series(1, 5)
    RETURNING ARRAY_AGG(id) INTO v_task_ids;
    
    -- Assign tasks to user (simulate batch fetch)
    UPDATE public.sms_tasks
    SET status = 'assigned', assigned_to = v_user_id, assigned_at = NOW()
    WHERE id = ANY(v_task_ids);
    
    -- Create expired batch
    INSERT INTO public.batch_tasks (user_id, task_ids, status, expires_at)
    VALUES (v_user_id, v_task_ids, 'in_progress', NOW() - INTERVAL '1 hour')
    RETURNING id INTO v_batch_id;
    
    -- Trigger the expiration (simulate update)
    UPDATE public.batch_tasks SET status = 'in_progress' WHERE id = v_batch_id;
    
    -- Check if batch expired
    IF EXISTS (SELECT 1 FROM public.batch_tasks WHERE id = v_batch_id AND status = 'expired') THEN
        RAISE NOTICE '✓ Batch expiration trigger working correctly';
        
        -- Check if tasks released
        IF EXISTS (SELECT 1 FROM public.sms_tasks WHERE id = ANY(v_task_ids) AND status = 'pending') THEN
            RAISE NOTICE '✓ Tasks released back to pending successfully';
        ELSE
            RAISE WARNING '✗ Tasks NOT released properly';
        END IF;
    ELSE
        RAISE WARNING '✗ Batch expiration trigger NOT working';
    END IF;
    
    RAISE NOTICE '=================================================';
    RAISE NOTICE 'TEST 3: Withdrawal Validation Trigger';
    RAISE NOTICE '=================================================';
    
    -- Test withdrawal validation
    BEGIN
        -- Set user balance
        UPDATE public.users SET balance = 100.00 WHERE id = v_user_id;
        
        -- Try to withdraw more than balance (should fail)
        BEGIN
            INSERT INTO public.withdrawals (user_id, amount, method, account_details)
            VALUES (v_user_id, 150.00, 'bank', '{"account": "test"}'::jsonb);
            
            RAISE WARNING '✗ Withdrawal validation NOT working - allowed overdraft';
        EXCEPTION
            WHEN OTHERS THEN
                RAISE NOTICE '✓ Withdrawal validation working - rejected overdraft';
        END;
        
        -- Try valid withdrawal
        INSERT INTO public.withdrawals (user_id, amount, method, account_details)
        VALUES (v_user_id, 50.00, 'bank', '{"account": "test"}'::jsonb);
        
        -- Check balance deduction
        SELECT balance INTO v_final_balance FROM public.users WHERE id = v_user_id;
        
        IF v_final_balance = 50.00 THEN
            RAISE NOTICE '✓ Withdrawal balance deduction working correctly';
        ELSE
            RAISE WARNING '✗ Withdrawal balance deduction NOT working correctly';
        END IF;
        
    END;
    
    RAISE NOTICE '=================================================';
    RAISE NOTICE 'TEST 4: Referral Tier Update Trigger';
    RAISE NOTICE '=================================================';
    
    -- Test tier progression
    UPDATE public.users SET referral_count = 0 WHERE id = v_user_id;
    
    -- Should be tier 1 (Bronze)
    IF (SELECT current_tier FROM public.users WHERE id = v_user_id) = 1 THEN
        RAISE NOTICE '✓ Initial tier set correctly (Bronze)';
    END IF;
    
    -- Increase referrals to 11 (should become Silver)
    UPDATE public.users SET referral_count = 11 WHERE id = v_user_id;
    
    IF (SELECT current_tier FROM public.users WHERE id = v_user_id) = 2 THEN
        RAISE NOTICE '✓ Tier upgraded to Silver correctly';
    ELSE
        RAISE WARNING '✗ Tier upgrade to Silver NOT working';
    END IF;
    
    -- Increase referrals to 51 (should become Gold)
    UPDATE public.users SET referral_count = 51 WHERE id = v_user_id;
    
    IF (SELECT current_tier FROM public.users WHERE id = v_user_id) = 3 THEN
        RAISE NOTICE '✓ Tier upgraded to Gold correctly';
    ELSE
        RAISE WARNING '✗ Tier upgrade to Gold NOT working';
    END IF;
    
END $$;

-- ============================================
-- PART 2: RPC FUNCTION TESTS
-- ============================================

DO $$
DECLARE
    v_user_id UUID;
    v_tasks RECORD;
    v_result JSONB;
    v_task_count INTEGER;
    v_initial_balance DECIMAL(10, 2);
    v_final_balance DECIMAL(10, 2);
BEGIN
    RAISE NOTICE '=================================================';
    RAISE NOTICE 'TEST 5: fetch_batch_tasks RPC Function';
    RAISE NOTICE '=================================================';
    
    -- Create test user
    INSERT INTO public.users (phone, email, password, device_id, balance)
    VALUES ('TEST2234567890', 'test2@test.com', 'test123', 'device-test-2', 0.00)
    RETURNING id INTO v_user_id;
    
    -- Create 15 test SMS tasks
    INSERT INTO public.sms_tasks (recipient, message, reward, status, priority)
    SELECT 
        'TEST2' || generate_series,
        'Test message ' || generate_series,
        (generate_series % 3 + 1)::DECIMAL,
        'pending',
        generate_series % 5
    FROM generate_series(1, 15);
    
    -- Fetch batch of 10 tasks
    SELECT COUNT(*) INTO v_task_count
    FROM public.fetch_batch_tasks(v_user_id, 10);
    
    IF v_task_count = 10 THEN
        RAISE NOTICE '✓ fetch_batch_tasks returned correct number of tasks (10)';
    ELSE
        RAISE WARNING '✗ fetch_batch_tasks returned % tasks instead of 10', v_task_count;
    END IF;
    
    -- Verify tasks are assigned
    SELECT COUNT(*) INTO v_task_count
    FROM public.sms_tasks
    WHERE assigned_to = v_user_id AND status = 'assigned';
    
    IF v_task_count = 10 THEN
        RAISE NOTICE '✓ Tasks assigned correctly to user';
    ELSE
        RAISE WARNING '✗ Task assignment failed';
    END IF;
    
    -- Verify batch record created
    IF EXISTS (
        SELECT 1 FROM public.batch_tasks
        WHERE user_id = v_user_id AND status = 'in_progress'
    ) THEN
        RAISE NOTICE '✓ Batch record created successfully';
    ELSE
        RAISE WARNING '✗ Batch record NOT created';
    END IF;
    
    -- Test concurrent fetch (should fail)
    BEGIN
        PERFORM public.fetch_batch_tasks(v_user_id, 5);
        RAISE WARNING '✗ Concurrent batch fetch NOT prevented';
    EXCEPTION
        WHEN OTHERS THEN
            RAISE NOTICE '✓ Concurrent batch fetch prevented correctly';
    END;
    
    RAISE NOTICE '=================================================';
    RAISE NOTICE 'TEST 6: submit_batch_results RPC Function';
    RAISE NOTICE '=================================================';
    
    -- Get assigned task IDs
    DECLARE
        v_success_ids UUID[];
        v_fail_ids UUID[];
    BEGIN
        SELECT ARRAY_AGG(id) INTO v_success_ids
        FROM (
            SELECT id FROM public.sms_tasks
            WHERE assigned_to = v_user_id AND status = 'assigned'
            LIMIT 8
        ) t;
        
        SELECT ARRAY_AGG(id) INTO v_fail_ids
        FROM (
            SELECT id FROM public.sms_tasks
            WHERE assigned_to = v_user_id AND status = 'assigned'
            LIMIT 2 OFFSET 8
        ) t;
        
        -- Get initial balance
        SELECT balance INTO v_initial_balance
        FROM public.users WHERE id = v_user_id;
        
        -- Submit batch results
        SELECT public.submit_batch_results(v_user_id, v_success_ids, v_fail_ids)
        INTO v_result;
        
        IF (v_result->>'success')::BOOLEAN THEN
            RAISE NOTICE '✓ submit_batch_results completed successfully';
            RAISE NOTICE '  Success: %, Fail: %, Reward: ₹%',
                v_result->>'success_count',
                v_result->>'fail_count',
                v_result->>'reward_earned';
        ELSE
            RAISE WARNING '✗ submit_batch_results failed: %', v_result->>'error';
        END IF;
        
        -- Verify balance updated
        SELECT balance INTO v_final_balance
        FROM public.users WHERE id = v_user_id;
        
        IF v_final_balance > v_initial_balance THEN
            RAISE NOTICE '✓ User balance updated correctly';
        ELSE
            RAISE WARNING '✗ User balance NOT updated';
        END IF;
        
        -- Verify batch marked as completed
        IF EXISTS (
            SELECT 1 FROM public.batch_tasks
            WHERE user_id = v_user_id AND status = 'completed'
        ) THEN
            RAISE NOTICE '✓ Batch marked as completed';
        ELSE
            RAISE WARNING '✗ Batch NOT marked as completed';
        END IF;
        
        -- Verify transaction created
        IF EXISTS (
            SELECT 1 FROM public.transactions
            WHERE user_id = v_user_id AND type = 'earning'
        ) THEN
            RAISE NOTICE '✓ Transaction record created';
        ELSE
            RAISE WARNING '✗ Transaction record NOT created';
        END IF;
        
        -- Verify failed tasks reset to pending
        IF EXISTS (
            SELECT 1 FROM public.sms_tasks
            WHERE id = ANY(v_fail_ids) AND status = 'pending'
        ) THEN
            RAISE NOTICE '✓ Failed tasks reset to pending';
        ELSE
            RAISE WARNING '✗ Failed tasks NOT reset properly';
        END IF;
    END;
    
    RAISE NOTICE '=================================================';
    RAISE NOTICE 'TEST 7: Daily Check-in Function';
    RAISE NOTICE '=================================================';
    
    -- Test daily check-in
    SELECT public.claim_daily_checkin(v_user_id) INTO v_result;
    
    IF (v_result->>'success')::BOOLEAN THEN
        RAISE NOTICE '✓ Daily check-in claimed successfully';
        RAISE NOTICE '  Reward: ₹%, Streak: % days',
            v_result->>'reward',
            v_result->>'streak';
    ELSE
        RAISE WARNING '✗ Daily check-in failed: %', v_result->>'message';
    END IF;
    
    -- Try to claim again (should fail)
    SELECT public.claim_daily_checkin(v_user_id) INTO v_result;
    
    IF NOT (v_result->>'success')::BOOLEAN THEN
        RAISE NOTICE '✓ Duplicate check-in prevented correctly';
    ELSE
        RAISE WARNING '✗ Duplicate check-in NOT prevented';
    END IF;
    
END $$;

-- ============================================
-- PART 3: REFERRAL LEADERBOARD TESTS
-- ============================================

DO $$
DECLARE
    v_users UUID[3];
    v_position RECORD;
    v_top_referrers RECORD;
    v_count INTEGER;
BEGIN
    RAISE NOTICE '=================================================';
    RAISE NOTICE 'TEST 8: Referral Leaderboard System';
    RAISE NOTICE '=================================================';
    
    -- Create 3 test users with different referral counts
    INSERT INTO public.users (phone, email, password, device_id, referral_count, referral_code)
    VALUES 
        ('TEST3234567890', 'test3@test.com', 'test123', 'device-test-3', 25, 'TEST3234567890'),
        ('TEST4234567890', 'test4@test.com', 'test123', 'device-test-4', 45, 'TEST4234567890'),
        ('TEST5234567890', 'test5@test.com', 'test123', 'device-test-5', 55, 'TEST5234567890')
    RETURNING ARRAY[id] INTO v_users;
    
    -- Refresh leaderboard
    REFRESH MATERIALIZED VIEW CONCURRENTLY public.referral_leaderboard;
    
    -- Check if users appear in leaderboard
    SELECT COUNT(*) INTO v_count
    FROM public.referral_leaderboard
    WHERE phone LIKE 'TEST%';
    
    IF v_count >= 3 THEN
        RAISE NOTICE '✓ Test users appear in leaderboard';
    ELSE
        RAISE WARNING '✗ Test users NOT in leaderboard (found %)', v_count;
    END IF;
    
    -- Test get_user_leaderboard_position
    SELECT * INTO v_position
    FROM public.get_user_leaderboard_position('TEST4234567890')
    LIMIT 1;
    
    IF v_position.user_rank IS NOT NULL THEN
        RAISE NOTICE '✓ User leaderboard position retrieved successfully';
        RAISE NOTICE '  Rank: %, Referrals: %, Tier: %',
            v_position.user_rank,
            v_position.user_referrals,
            v_position.user_tier_name;
    ELSE
        RAISE WARNING '✗ User leaderboard position NOT retrieved';
    END IF;
    
    -- Test get_top_referrers
    SELECT COUNT(*) INTO v_count
    FROM public.get_top_referrers(10)
    WHERE phone LIKE 'TEST%';
    
    IF v_count >= 3 THEN
        RAISE NOTICE '✓ Top referrers function working correctly';
    ELSE
        RAISE WARNING '✗ Top referrers function NOT working correctly';
    END IF;
    
    -- Test tier assignments
    IF EXISTS (
        SELECT 1 FROM public.users
        WHERE phone = 'TEST3234567890' AND current_tier = 2
    ) THEN
        RAISE NOTICE '✓ Tier 2 (Silver) assigned correctly for 25 referrals';
    ELSE
        RAISE WARNING '✗ Tier 2 NOT assigned correctly';
    END IF;
    
    IF EXISTS (
        SELECT 1 FROM public.users
        WHERE phone = 'TEST5234567890' AND current_tier = 3
    ) THEN
        RAISE NOTICE '✓ Tier 3 (Gold) assigned correctly for 55 referrals';
    ELSE
        RAISE WARNING '✗ Tier 3 NOT assigned correctly';
    END IF;
    
END $$;

-- ============================================
-- PART 4: PERFORMANCE TESTS
-- ============================================

DO $$
DECLARE
    v_start TIMESTAMP;
    v_end TIMESTAMP;
    v_duration INTERVAL;
    v_user_id UUID;
    v_task_count INTEGER;
BEGIN
    RAISE NOTICE '=================================================';
    RAISE NOTICE 'TEST 9: Performance and Concurrency';
    RAISE NOTICE '=================================================';
    
    -- Create test user
    INSERT INTO public.users (phone, email, password, device_id)
    VALUES ('TEST6234567890', 'test6@test.com', 'test123', 'device-test-6')
    RETURNING id INTO v_user_id;
    
    -- Create 1000 test tasks
    v_start := clock_timestamp();
    
    INSERT INTO public.sms_tasks (recipient, message, reward, status, priority)
    SELECT 
        'TEST6' || generate_series,
        'Performance test message ' || generate_series,
        1.00,
        'pending',
        (generate_series % 10)
    FROM generate_series(1, 1000);
    
    v_end := clock_timestamp();
    v_duration := v_end - v_start;
    
    RAISE NOTICE '✓ Inserted 1000 tasks in % seconds', EXTRACT(EPOCH FROM v_duration);
    
    -- Test batch fetch performance
    v_start := clock_timestamp();
    
    SELECT COUNT(*) INTO v_task_count
    FROM public.fetch_batch_tasks(v_user_id, 50);
    
    v_end := clock_timestamp();
    v_duration := v_end - v_start;
    
    IF v_task_count = 50 THEN
        RAISE NOTICE '✓ Fetched batch of 50 tasks in % seconds', EXTRACT(EPOCH FROM v_duration);
    ELSE
        RAISE WARNING '✗ Batch fetch returned incorrect count: %', v_task_count;
    END IF;
    
    -- Test index usage
    RAISE NOTICE '=================================================';
    RAISE NOTICE 'Index Usage Statistics:';
    RAISE NOTICE '=================================================';
    
    PERFORM 
        RAISE NOTICE '  % on % - Scans: %, Size: %',
            indexname,
            tablename,
            idx_scan,
            pg_size_pretty(pg_relation_size(indexrelid))
    FROM pg_stat_user_indexes
    WHERE schemaname = 'public'
        AND idx_scan > 0
        AND tablename IN ('sms_tasks', 'batch_tasks', 'users', 'transactions')
    ORDER BY idx_scan DESC
    LIMIT 10;
    
END $$;

-- ============================================
-- PART 5: CLEANUP EXPIRED BATCHES TEST
-- ============================================

DO $$
DECLARE
    v_count INTEGER;
BEGIN
    RAISE NOTICE '=================================================';
    RAISE NOTICE 'TEST 10: Scheduled Cleanup Function';
    RAISE NOTICE '=================================================';
    
    -- Run cleanup function
    SELECT public.cleanup_all_expired_batches() INTO v_count;
    
    RAISE NOTICE '✓ Cleanup function processed % expired batches', v_count;
    
    -- Verify no in_progress batches are expired
    SELECT COUNT(*) INTO v_count
    FROM public.batch_tasks
    WHERE status = 'in_progress' AND expires_at < NOW();
    
    IF v_count = 0 THEN
        RAISE NOTICE '✓ All expired batches cleaned up successfully';
    ELSE
        RAISE WARNING '✗ Still have % expired in_progress batches', v_count;
    END IF;
    
END $$;

-- ============================================
-- FINAL CLEANUP
-- ============================================

DO $$
BEGIN
    RAISE NOTICE '=================================================';
    RAISE NOTICE 'CLEANING UP TEST DATA';
    RAISE NOTICE '=================================================';
    
    PERFORM cleanup_test_data();
    
    RAISE NOTICE '=================================================';
    RAISE NOTICE 'ALL TESTS COMPLETED';
    RAISE NOTICE '=================================================';
    RAISE NOTICE 'Review the output above for any warnings (✗)';
    RAISE NOTICE 'All tests with checkmarks (✓) passed successfully';
    RAISE NOTICE '=================================================';
END $$;

-- Drop cleanup function
DROP FUNCTION IF EXISTS cleanup_test_data();
