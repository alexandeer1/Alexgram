package tw.nekomimi.nekogram.settings;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class GlassGroupDecoration extends RecyclerView.ItemDecoration {

    public interface SeparatorChecker {
        boolean isSeparator(int viewType);
    }

    private final SeparatorChecker checker;
    private final Paint fillPaint;
    private final Paint borderPaint;
    private final Paint dividerPaint;
    private final float cornerRadius;
    private final int horizontalMargin;
    private final RectF rectF = new RectF();

    public GlassGroupDecoration(SeparatorChecker checker) {
        this.checker = checker;

        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(AndroidUtilities.dp(0.5f));
        dividerPaint = new Paint();

        cornerRadius = AndroidUtilities.dp(16);
        horizontalMargin = AndroidUtilities.dp(16);

        updateColors();
    }

    private void updateColors() {
        boolean isDark = Theme.getActiveTheme().isDark();
        fillPaint.setColor(isDark ? 0x28FFFFFF : 0x40FFFFFF);
        borderPaint.setColor(isDark ? 0x18FFFFFF : 0x28FFFFFF);
        dividerPaint.setColor(isDark ? 0x12FFFFFF : 0x15000000);
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                               @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        int pos = parent.getChildAdapterPosition(view);
        RecyclerView.Adapter<?> adapter = parent.getAdapter();
        if (adapter == null || pos == RecyclerView.NO_POSITION) return;

        if (checker.isSeparator(adapter.getItemViewType(pos))) {
            outRect.set(0, 0, 0, 0);
        } else {
            outRect.set(horizontalMargin, 0, horizontalMargin, 0);
        }
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        RecyclerView.Adapter<?> adapter = parent.getAdapter();
        if (adapter == null) return;
        updateColors();

        int childCount = parent.getChildCount();
        int i = 0;

        while (i < childCount) {
            View child = parent.getChildAt(i);
            int pos = parent.getChildAdapterPosition(child);

            if (pos == RecyclerView.NO_POSITION || checker.isSeparator(adapter.getItemViewType(pos))) {
                i++;
                continue;
            }

            // Found start of a visible group
            float top = child.getTop() + child.getTranslationY();
            float bottom = child.getBottom() + child.getTranslationY();
            float left = child.getLeft();
            float right = child.getRight();

            int j = i + 1;
            while (j < childCount) {
                View next = parent.getChildAt(j);
                int nextPos = parent.getChildAdapterPosition(next);
                if (nextPos == RecyclerView.NO_POSITION || checker.isSeparator(adapter.getItemViewType(nextPos))) {
                    break;
                }
                bottom = next.getBottom() + next.getTranslationY();
                left = Math.min(left, next.getLeft());
                right = Math.max(right, next.getRight());
                j++;
            }

            // Draw glass background
            rectF.set(left, top, right, bottom);
            c.drawRoundRect(rectF, cornerRadius, cornerRadius, fillPaint);
            c.drawRoundRect(rectF, cornerRadius, cornerRadius, borderPaint);

            // Draw thin dividers between items within the group
            for (int k = i; k < j - 1; k++) {
                View dv = parent.getChildAt(k);
                float dy = dv.getBottom() + dv.getTranslationY();
                c.drawRect(left + AndroidUtilities.dp(17), dy,
                        right - AndroidUtilities.dp(17), dy + AndroidUtilities.dp(0.5f),
                        dividerPaint);
            }

            i = j;
        }
    }
}
