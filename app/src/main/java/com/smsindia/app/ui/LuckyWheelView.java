package com.smsindia.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class LuckyWheelView extends View {

    private int mWidth;
    private int mHeight;
    private Paint mPaint;
    private Paint mTextPaint;
    private Paint mBorderPaint; // Paint for the Gold Border
    private Paint mCenterPaint; // Paint for the Center Circle
    
    private String[] mTitles = {"₹0.6", "₹0.8", "₹10", "₹0", "₹100", "₹0.6"};
    
    // UPDATED COLORS: Alternating Dark Green and Gold
    private int[] mColors = {
            Color.parseColor("#1B5E20"), // Dark Green
            Color.parseColor("#FFC107"), // Gold
            Color.parseColor("#1B5E20"), // Dark Green
            Color.parseColor("#FFC107"), // Gold
            Color.parseColor("#1B5E20"), // Dark Green
            Color.parseColor("#FFC107")  // Gold
    };
    
    // We start angle at 270 (top)
    private float mStartAngle = 0;

    public LuckyWheelView(Context context) {
        this(context, null);
    }

    public LuckyWheelView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        // Slice Paint
        mPaint = new Paint();
        mPaint.setAntiAlias(true);

        // Text Paint
        mTextPaint = new Paint();
        mTextPaint.setTextSize(50f); // Adjusted size slightly
        mTextPaint.setAntiAlias(true);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setFakeBoldText(true);

        // Border Paint (THICK GOLD BORDER)
        mBorderPaint = new Paint();
        mBorderPaint.setColor(Color.parseColor("#FFC107")); // Gold
        mBorderPaint.setStyle(Paint.Style.STROKE);
        mBorderPaint.setStrokeWidth(20f); // 20px Thickness
        mBorderPaint.setAntiAlias(true);
        
        // Center Circle Paint
        mCenterPaint = new Paint();
        mCenterPaint.setColor(Color.WHITE);
        mCenterPaint.setStyle(Paint.Style.FILL);
        mCenterPaint.setAntiAlias(true);
        mCenterPaint.setShadowLayer(4f, 0f, 2f, Color.GRAY);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mWidth == 0) return;

        int radius = Math.min(mWidth, mHeight) / 2;
        // Reduce radius slightly to fit the thick border inside the view
        int contentRadius = radius - 15; 
        
        int centerX = mWidth / 2;
        int centerY = mHeight / 2;
        
        RectF range = new RectF(centerX - contentRadius, centerY - contentRadius, centerX + contentRadius, centerY + contentRadius);

        float sweepAngle = 360f / mTitles.length;

        for (int i = 0; i < mTitles.length; i++) {
            // 1. Draw Slice Background
            mPaint.setColor(mColors[i % mColors.length]);
            canvas.drawArc(range, mStartAngle + (i * sweepAngle), sweepAngle, true, mPaint);
            
            // 2. Determine Text Color for readability
            // If background is Green -> Text White. If Gold -> Text Dark Green.
            if (mColors[i % mColors.length] == Color.parseColor("#1B5E20")) {
                mTextPaint.setColor(Color.WHITE);
            } else {
                mTextPaint.setColor(Color.parseColor("#1B5E20"));
            }

            // 3. Draw Text
            drawText(canvas, mStartAngle + (i * sweepAngle), sweepAngle, mTitles[i], contentRadius, centerX, centerY);
        }
        
        // 4. Draw The Thick Gold Border
        canvas.drawCircle(centerX, centerY, contentRadius, mBorderPaint);
        
        // 5. Draw Center Cap (Small White Circle in middle)
        canvas.drawCircle(centerX, centerY, 20f, mCenterPaint);
    }

    private void drawText(Canvas canvas, float startAngle, float sweepAngle, String text, int radius, int centerX, int centerY) {
        float angle = (float) Math.toRadians(startAngle + sweepAngle / 2);
        float x = (float) (centerX + (radius * 0.70) * Math.cos(angle)); // 0.70 to center text inside slice
        float y = (float) (centerY + (radius * 0.70) * Math.sin(angle));

        // Center text vertically
        float textHeight = mTextPaint.descent() - mTextPaint.ascent();
        float textOffset = (textHeight / 2) - mTextPaint.descent();

        canvas.drawText(text, x, y + textOffset, mTextPaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        mWidth = getMeasuredWidth();
        mHeight = getMeasuredHeight();
    }
}
