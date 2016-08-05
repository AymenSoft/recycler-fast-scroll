package com.futuremind.recyclerviewfastscroll;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.widget.TextViewCompat;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Created by mklimczak on 28/07/15.
 */
public class FastScroller extends LinearLayout {

    private final RecyclerScrollListener scrollListener = new RecyclerScrollListener(this);
    private RecyclerView recyclerView;

    private FastScrollBubble bubble;
    private View handle;

    private int bubbleOffset;

    private int handleColor;
    private int bubbleColor;
    private int bubbleTextAppearance;
    private int scrollerOrientation;

    private boolean manuallyChangingPosition;

    private SectionTitleProvider titleProvider;

    public FastScroller(Context context) {
        this(context, null);
    }

    public FastScroller(Context context, AttributeSet attrs) {
        super(context, attrs);
        setClipChildren(false);
        LayoutInflater inflater = LayoutInflater.from(getContext());
        inflater.inflate(R.layout.fastscroll__scroller, this);
        TypedArray style = context.obtainStyledAttributes(attrs, R.styleable.fastscroll__fastScroller, R.attr.fastscroll__style, 0);
        try {
            bubbleColor = style.getColor(R.styleable.fastscroll__fastScroller_fastscroll__bubbleColor, ContextCompat.getColor(context, android.R.color.white));
            handleColor = style.getColor(R.styleable.fastscroll__fastScroller_fastscroll__handleColor, ContextCompat.getColor(context, android.R.color.darker_gray));
            bubbleTextAppearance = style.getResourceId(R.styleable.fastscroll__fastScroller_fastscroll__bubbleTextAppearance, android.R.style.TextAppearance);
        }
        finally {
            style.recycle();
        }
    }

    /**
     * Attach the {@link FastScroller} to {@link RecyclerView}. Should be used after the adapter is set
     * to the {@link RecyclerView}. If the adapter implements SectionTitleProvider, the FastScroller
     * will show a bubble with title.
     * @param recyclerView A {@link RecyclerView} to attach the {@link FastScroller} to.
     */
    public void setRecyclerView(RecyclerView recyclerView) {
        this.recyclerView = recyclerView;
        if(recyclerView.getAdapter() instanceof SectionTitleProvider) titleProvider = (SectionTitleProvider) recyclerView.getAdapter();
        recyclerView.addOnScrollListener(scrollListener);
        invalidateVisibility();
        recyclerView.setOnHierarchyChangeListener(new OnHierarchyChangeListener() {
            @Override
            public void onChildViewAdded(View parent, View child) {
                invalidateVisibility();
            }

            @Override
            public void onChildViewRemoved(View parent, View child) {
                invalidateVisibility();
            }
        });
    }

    /**
     * Set the orientation of the {@link FastScroller}. The orientation of the {@link FastScroller}
     * should generally match the orientation of connected  {@link RecyclerView} for good UX but it's not enforced.
     * Note: This method is overridden from {@link LinearLayout#setOrientation(int)} but for {@link FastScroller}
     * it has a totally different meaning.
     * @param orientation of the {@link FastScroller}. {@link #VERTICAL} or {@link #HORIZONTAL}
     */
    @Override
    public void setOrientation(int orientation) {
        scrollerOrientation = orientation;
        //switching orientation, because orientation in linear layout
        //is something different than orientation of fast scroller
        super.setOrientation(orientation == HORIZONTAL ? VERTICAL : HORIZONTAL);
    }

    /**
     * Set the background color of the bubble.
     * @param color Color in hex notation with alpha channel, e.g. 0xFFFFFFFF
     */
    public void setBubbleColor(int color) {
        bubbleColor = color;
        invalidate();
    }

    /**
     * Set the background color of the handle.
     * @param color Color in hex notation with alpha channel, e.g. 0xFFFFFFFF
     */
    public void setHandleColor(int color) {
        handleColor = color;
        invalidate();
    }

    /**
     * Sets the text appearance of the bubble.
     * @param textAppearanceResourceId The id of the resource to be used as text appearance of the bubble.
     */
    public void setBubbleTextAppearance(int textAppearanceResourceId){
        bubbleTextAppearance = textAppearanceResourceId;
        invalidate();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        bubble = (FastScrollBubble) findViewById(R.id.fastscroller_bubble);
        handle = findViewById(R.id.fastscroller_handle);
        TextView defaultBubble = (TextView) bubble.getChildAt(0);

        calculateSizes();
        initHandleBackground();
        initHandleMovement();

        setBackgroundTint(defaultBubble, bubbleColor);
        setBackgroundTint(handle, handleColor);
        TextViewCompat.setTextAppearance(defaultBubble, bubbleTextAppearance);

        scrollListener.updateHandlePosition(recyclerView);
    }

    private void calculateSizes() {
        int handleWidth = getResources().getDimensionPixelSize(isVertical() ? R.dimen.fastscroll__handle_clickable_width : R.dimen.fastscroll__handle_height);
        int handleHeight = getResources().getDimensionPixelSize(isVertical() ? R.dimen.fastscroll__handle_height : R.dimen.fastscroll__handle_clickable_width);
        handle.getLayoutParams().width = handleWidth;
        handle.getLayoutParams().height = handleHeight;
        bubbleOffset = (int) (isVertical() ? ((float)handleHeight/2f)-bubble.getHeight() : ((float)handleWidth/2f)-bubble.getWidth());
    }

    private void initHandleBackground() {
        int verticalInset = isVertical() ? 0 : getResources().getDimensionPixelSize(R.dimen.fastscroll__handle_inset);
        int horizontalInset = !isVertical() ? 0 : getResources().getDimensionPixelSize(R.dimen.fastscroll__handle_inset);
        InsetDrawable handleBg = new InsetDrawable(ContextCompat.getDrawable(getContext(), R.drawable.fastscroll__handle), horizontalInset, verticalInset, horizontalInset, verticalInset);
        Utils.setBackground(handle, handleBg);
    }

    private void setBackgroundTint(View view, int color) {
        final Drawable background = DrawableCompat.wrap(view.getBackground());
        DrawableCompat.setTint(background, color);
        Utils.setBackground(view, background);
    }

    private void initHandleMovement() {
        handle.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {

                requestDisallowInterceptTouchEvent(true);

                if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {

                    if(titleProvider!=null) bubble.show();
                    manuallyChangingPosition = true;

                    float relativePos = getRelativeTouchPosition(event);
                    setScrollerPosition(relativePos);
                    setRecyclerViewPosition(relativePos);

                    return true;

                } else if (event.getAction() == MotionEvent.ACTION_UP) {

                    manuallyChangingPosition = false;
                    if(titleProvider!=null) bubble.hide();
                    return true;

                }

                return false;

            }
        });
    }

    private float getRelativeTouchPosition(MotionEvent event){
        if(isVertical()){
            float yInParent = event.getRawY() - Utils.getViewRawY(handle);
            return yInParent / (getHeight() - handle.getHeight());
        } else {
            float xInParent = event.getRawX() - Utils.getViewRawX(handle);
            return xInParent / (getWidth() - handle.getWidth());
        }
    }

    private void invalidateVisibility() {
        if(
                recyclerView.getAdapter()==null ||
                recyclerView.getAdapter().getItemCount()==0 ||
                recyclerView.getChildAt(0)==null ||
                isRecyclerViewNotScrollable()
                ){
            setVisibility(GONE);
        } else {
            setVisibility(VISIBLE);
        }
    }

    private boolean isRecyclerViewNotScrollable() {
        if(isVertical()) {
            return recyclerView.getChildAt(0).getHeight() * recyclerView.getAdapter().getItemCount() <= recyclerView.getHeight();
        } else {
            return recyclerView.getChildAt(0).getWidth() * recyclerView.getAdapter().getItemCount() <= recyclerView.getWidth();
        }
    }

    private void setRecyclerViewPosition(float relativePos) {
        if (recyclerView != null) {
            int itemCount = recyclerView.getAdapter().getItemCount();
            int targetPos = (int) Utils.getValueInRange(0, itemCount - 1, (int) (relativePos * (float) itemCount));
            recyclerView.scrollToPosition(targetPos);
            if(titleProvider!=null) bubble.setText(titleProvider.getSectionTitle(targetPos));
        }
    }

    void setScrollerPosition(float relativePos) {
        if(isVertical()) {
            bubble.setY(Utils.getValueInRange(
                    0,
                    getHeight() - bubble.getHeight(),
                    relativePos * (getHeight() - handle.getHeight()) + bubbleOffset)
            );
            handle.setY(Utils.getValueInRange(
                    0,
                    getHeight() - handle.getHeight(),
                    relativePos * (getHeight() - handle.getHeight()))
            );
        } else {
            bubble.setX(Utils.getValueInRange(
                    0,
                    getWidth() - bubble.getWidth(),
                    relativePos * (getWidth() - handle.getWidth()) + bubbleOffset)
            );
            handle.setX(Utils.getValueInRange(
                    0,
                    getWidth() - handle.getWidth(),
                    relativePos * (getWidth() - handle.getWidth()))
            );
        }
    }

    boolean isVertical(){
        return scrollerOrientation == VERTICAL;
    }

    boolean shouldUpdateHandlePosition() {
        return handle!=null && !manuallyChangingPosition && recyclerView.getChildCount() > 0;
    }
}
