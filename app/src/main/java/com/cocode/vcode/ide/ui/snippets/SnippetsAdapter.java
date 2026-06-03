package com.cocode.vcode.ide.ui.snippets;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.model.FileType;
import com.cocode.vcode.ide.data.model.SnippetItem;
import com.cocode.vcode.ide.databinding.ItemSnippetBinding;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * SnippetsAdapter manages the display of reusable code snippets.
 * It supports horizontal swipe physics for editing and deleting snippets,
 * and features language-specific badges for quick identification.
 */
public class SnippetsAdapter extends RecyclerView.Adapter<SnippetsAdapter.SnippetViewHolder> {

    private final SnippetListener listener;
    private List<SnippetItem> snippets = new ArrayList<>();

    /**
     * Tracks the currently swiped-open snippet card.
     */
    private View currentlySwipedView = null;

    public SnippetsAdapter(SnippetListener listener) {
        this.listener = listener;
    }

    /**
     * Updates the snippet list data.
     */
    public void setSnippets(List<SnippetItem> snippets) {
        this.snippets = snippets != null ? snippets : new ArrayList<>();
        notifyDataSetChanged();
    }

    /**
     * Smoothly animates the currently swiped item back to its closed position.
     */
    public void closeSwipedItem() {
        if (currentlySwipedView != null) {
            currentlySwipedView.animate().translationX(0).setDuration(200).start();
            currentlySwipedView = null;
        }
    }

    @NonNull
    @Override
    public SnippetViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSnippetBinding binding = ItemSnippetBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new SnippetViewHolder(binding, this);
    }

    @Override
    public void onBindViewHolder(@NonNull SnippetViewHolder holder, int position) {
        holder.bind(snippets.get(position));
    }

    @Override
    public int getItemCount() {
        return snippets.size();
    }

    /**
     * Listener interface for snippet interaction events.
     */
    public interface SnippetListener {
        void onSnippetClick(SnippetItem snippet);

        void onSnippetEditClick(SnippetItem snippet);

        void onSnippetDeleteClick(SnippetItem snippet);
    }

    /**
     * ViewHolder for representing a single code snippet card.
     */
    public static class SnippetViewHolder extends RecyclerView.ViewHolder {
        private final ItemSnippetBinding binding;
        private final SnippetsAdapter adapter;

        @SuppressLint("ClickableViewAccessibility")
        public SnippetViewHolder(@NonNull ItemSnippetBinding binding, SnippetsAdapter adapter) {
            super(binding.getRoot());
            this.binding = binding;
            this.adapter = adapter;

            setupCircularActionButtons(itemView.getContext());

            // Apply consistent UI typography
            binding.tvSnippetTitle.setTypeface(FontManager.getInstance().getUiSemiBold(itemView.getContext()));
            binding.tvSnippetPreview.setTypeface(FontManager.getInstance().getUiMedium(itemView.getContext()));
            binding.tvLanguageBadge.setTypeface(FontManager.getInstance().getUiSemiBold(itemView.getContext()));

            setupSwipeListeners();
        }

        /**
         * Configures the visual style of the edit and delete action buttons.
         */
        private void setupCircularActionButtons(Context context) {
            GradientDrawable editBg = new GradientDrawable();
            editBg.setShape(GradientDrawable.OVAL);
            editBg.setColor(ContextCompat.getColor(context, R.color.vcode_accent_primary));
            binding.btnActionEdit.setBackground(editBg);

            GradientDrawable deleteBg = new GradientDrawable();
            deleteBg.setShape(GradientDrawable.OVAL);
            deleteBg.setColor(ContextCompat.getColor(context, R.color.vcode_accent_error));
            binding.btnActionDelete.setBackground(deleteBg);
        }

        /**
         * Implements custom horizontal swipe physics for revealable actions.
         */
        @SuppressLint("ClickableViewAccessibility")
        private void setupSwipeListeners() {
            binding.snippetCardView.setOnTouchListener(new View.OnTouchListener() {
                float startX = 0, startY = 0, startTranslateX = 0;
                boolean isSwiping = false;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            // Close other swiped items before starting a new interaction
                            if (adapter.currentlySwipedView != null && adapter.currentlySwipedView != v)
                                adapter.closeSwipedItem();
                            startX = event.getRawX();
                            startY = event.getRawY();
                            startTranslateX = v.getTranslationX();
                            isSwiping = false;
                            v.animate().cancel();
                            return true;

                        case MotionEvent.ACTION_MOVE:
                            float dX = event.getRawX() - startX;
                            float dY = event.getRawY() - startY;

                            // Horizontal intent threshold
                            if (Math.abs(dX) > 15 && Math.abs(dX) > Math.abs(dY)) {
                                isSwiping = true;
                                v.getParent().requestDisallowInterceptTouchEvent(true);
                            } else if (Math.abs(dY) > 15 && !isSwiping) {
                                v.getParent().requestDisallowInterceptTouchEvent(false);
                            }

                            if (isSwiping) {
                                float newTranslateX = startTranslateX + dX;
                                float maxSwipe = binding.layoutActions.getWidth();
                                if (maxSwipe == 0)
                                    maxSwipe = UiUtils.dpToPx(itemView.getContext(), 100);

                                // Clamp translation to left-swipe menu width
                                if (newTranslateX < -maxSwipe) newTranslateX = -maxSwipe;
                                if (newTranslateX > 0) newTranslateX = 0;
                                v.setTranslationX(newTranslateX);
                            }
                            return true;

                        case MotionEvent.ACTION_UP:
                            if (!isSwiping) {
                                // Interpret as a tap if movement was negligible
                                float finalDx = Math.abs(event.getRawX() - startX);
                                float finalDy = Math.abs(event.getRawY() - startY);
                                if (finalDx < 15 && finalDy < 15) {
                                    int pos = getBindingAdapterPosition();
                                    if (pos != RecyclerView.NO_POSITION && adapter.listener != null) {
                                        adapter.listener.onSnippetClick(adapter.snippets.get(pos));
                                    }
                                }
                            } else {
                                snapCardPosition(v);
                            }
                            return true;

                        case MotionEvent.ACTION_CANCEL:
                            if (isSwiping) snapCardPosition(v);
                            else v.animate().translationX(0).setDuration(200).start();
                            return true;
                    }
                    return false;
                }

                /**
                 * Snaps the card to either open or closed state based on translation progress.
                 */
                private void snapCardPosition(View v) {
                    float finalTranslateX = v.getTranslationX();
                    float maxW = binding.layoutActions.getWidth();
                    if (maxW == 0) maxW = UiUtils.dpToPx(itemView.getContext(), 100);

                    if (finalTranslateX < -maxW / 2) {
                        v.animate().translationX(-maxW).setDuration(200).start();
                        adapter.currentlySwipedView = v;
                    } else {
                        v.animate().translationX(0).setDuration(200).start();
                        if (adapter.currentlySwipedView == v) adapter.currentlySwipedView = null;
                    }
                }
            });

            binding.btnActionEdit.setOnClickListener(v -> {
                adapter.closeSwipedItem();
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && adapter.listener != null)
                    adapter.listener.onSnippetEditClick(adapter.snippets.get(pos));
            });

            binding.btnActionDelete.setOnClickListener(v -> {
                adapter.closeSwipedItem();
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && adapter.listener != null)
                    adapter.listener.onSnippetDeleteClick(adapter.snippets.get(pos));
            });
        }

        /**
         * Binds the snippet data to the card, including title, preview, and language badge.
         */
        public void bind(SnippetItem snippet) {
            binding.snippetCardView.setTranslationX(0); // Reset UI state on recycle

            binding.tvSnippetTitle.setText(snippet.getTitle());

            // Ellipsize the content preview for the card display
            String preview = snippet.getContent();
            if (preview != null && preview.length() > 25) {
                preview = preview.substring(0, 22) + "...";
            }
            binding.tvSnippetPreview.setText(preview);

            // Configure the language-specific visual badge
            FileType lang = snippet.getFileType();
            if (lang != null) {
                binding.tvLanguageBadge.setVisibility(View.VISIBLE);
                binding.tvLanguageBadge.setText(lang.getDisplayName());
                binding.tvLanguageBadge.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(itemView.getContext(), lang.getColorResId())));

                // Use high-contrast text coloring for specifically bright backgrounds like JS Yellow
                if (lang == FileType.JAVASCRIPT) {
                    binding.tvLanguageBadge.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.vcode_lang_on_js));
                } else {
                    binding.tvLanguageBadge.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.vcode_bg_surface));
                }
            } else {
                binding.tvLanguageBadge.setVisibility(View.GONE);
            }

            // Hide the delete action for hardcoded system snippets
            boolean isDefaultSnippet = snippet.getId() != null && snippet.getId().startsWith("def_");
            binding.btnActionDelete.setVisibility(isDefaultSnippet ? View.GONE : View.VISIBLE);
        }
    }
}