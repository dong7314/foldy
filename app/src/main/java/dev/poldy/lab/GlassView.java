package dev.poldy.lab;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Frozen local frame, perspective drift, asymmetric frost, then a clear reveal. */
final class GlassView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RuntimeShader shader;
    private float progress;
    private final boolean opening;
    private final Bitmap frame;
    private static final String SOURCE = """
        uniform shader image;
        uniform float2 resolution;
        uniform float2 sourceSize;
        uniform float phase;
        uniform float opening;
        half4 main(float2 p) {
            float2 uv = p / resolution;
            float t = clamp(phase, 0.0, 1.0);
            float remaining = 1.0 - t;
            float moving = 1.0 - smoothstep(0.12, 0.66, uv.x);
            float frost = moving * remaining;
            float2 q = uv;
            q.x = (q.x - 0.5) * (1.0 + 0.12 * frost) + 0.5;
            q.x += (opening > 0.5 ? -1.0 : 1.0) * 0.075 * frost;
            q.y = (q.y - 0.5) * (1.0 + 0.06 * frost * (1.0 - uv.x)) + 0.5;
            // FoldService supplies a frame from the destination layout, already matching this aspect ratio.
            float scale = resolution.x / sourceSize.x;
            float2 xy = q * sourceSize;
            float r = (2.0 + 24.0 * frost) / scale;
            half4 c = image.eval(xy) * 0.20;
            c += image.eval(xy + float2( r,0)) * 0.10;
            c += image.eval(xy + float2(-r,0)) * 0.10;
            c += image.eval(xy + float2(0, r)) * 0.10;
            c += image.eval(xy + float2(0,-r)) * 0.10;
            c += image.eval(xy + float2( r, r)*0.707) * 0.10;
            c += image.eval(xy + float2(-r, r)*0.707) * 0.10;
            c += image.eval(xy + float2( r,-r)*0.707) * 0.10;
            c += image.eval(xy + float2(-r,-r)*0.707) * 0.10;
            float wash = frost * 0.10;
            c.rgb = mix(c.rgb, half3(0.88,0.93,0.95), half(wash));
            float glint = exp(-pow((uv.x - (0.1 + 0.7*t))*25.0, 2.0));
            c.rgb += half3(0.025,0.030,0.035) * half(glint * remaining);
            float opacity = 1.0 - smoothstep(0.38,1.0,t);
            return c * half(opacity);
        }
        """;
    GlassView(Context context, Bitmap frame, boolean opening) {
        super(context); this.frame = frame; this.opening = opening;
        shader = new RuntimeShader(SOURCE);
        BitmapShader input = new BitmapShader(frame, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        input.setFilterMode(BitmapShader.FILTER_MODE_LINEAR);
        shader.setInputShader("image", input);
        shader.setFloatUniform("sourceSize", frame.getWidth(), frame.getHeight());
        shader.setFloatUniform("opening", opening ? 1f : 0f);
        paint.setShader(shader);
    }
    void progress(float value) { progress = value; invalidate(); }
    @Override protected void onDraw(Canvas canvas) {
        shader.setFloatUniform("resolution", Math.max(1,getWidth()), Math.max(1,getHeight()));
        shader.setFloatUniform("phase", progress);
        canvas.drawRect(0,0,getWidth(),getHeight(),paint);
    }
}
