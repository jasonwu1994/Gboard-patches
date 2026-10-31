package com.google.android.libraries.inputmethod.inputview;

public class InputView extends android.widget.FrameLayout {
    public InputView() { super(null); }

    public InputView(android.content.Context context) {
        super(context);
        setWillNotDraw(false);
    }

    /** 18.5.4 draws its navigation padding independently of the keyboard theme. */
    @Override protected void onDraw(android.graphics.Canvas canvas) {
        android.graphics.Paint paint = new android.graphics.Paint();
        paint.setColor(android.graphics.Color.RED);
        canvas.drawRect(0, getHeight() - getPaddingBottom(), getWidth(), getHeight(), paint);
        super.onDraw(canvas);
    }
}
