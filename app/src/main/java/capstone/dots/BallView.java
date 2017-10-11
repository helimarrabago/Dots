package capstone.dots;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

public class BallView extends View {
    public float mX;
    public float mY;
    private final int mR;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public BallView(Context context, float x, float y, int r) {
        super(context);
        mPaint.setColor(0xFF949494);
        this.mX = x;
        this.mY = y;
        this.mR = r;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawCircle(mX, mY, mR, mPaint);
    }
}
