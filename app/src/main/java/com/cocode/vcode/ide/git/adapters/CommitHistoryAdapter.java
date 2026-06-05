package com.cocode.vcode.ide.git.adapters;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.databinding.ItemCommitHistoryBinding;
import com.cocode.vcode.ide.git.model.CommitItem;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;

/**
 * Version control revision tree adapter.
 * Renders repository commit history as a linear timeline graph, dynamically shifting node styling
 * and connecting line segments based on the relative position of individual entries.
 */
public class CommitHistoryAdapter extends ListAdapter<CommitItem, CommitHistoryAdapter.ViewHolder> {

    private final CommitHistoryListener listener;

    public CommitHistoryAdapter(CommitHistoryListener listener) {
        super(new DiffUtil.ItemCallback<>() {
            @Override
            public boolean areItemsTheSame(@NonNull CommitItem old, @NonNull CommitItem n) {
                return old.getSha().equals(n.getSha());
            }

            @Override
            public boolean areContentsTheSame(@NonNull CommitItem old, @NonNull CommitItem n) {
                return old.getMessage().equals(n.getMessage());
            }
        });
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemCommitHistoryBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        // Evaluate tree boundaries by checking position flags against list bounds indices
        holder.bind(getItem(position), position == 0, position == getItemCount() - 1);
    }

    /**
     * Interception contract listening for item details expansion or checkout trigger updates.
     */
    public interface CommitHistoryListener {
        void onOverflowClick(CommitItem item, View anchor);

        void onCommitClick(CommitItem item);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemCommitHistoryBinding binding;

        ViewHolder(ItemCommitHistoryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(CommitItem item, boolean isFirst, boolean isLast) {
            Context context = itemView.getContext();

            // Set typography layouts
            binding.tvHeadBadge.setTypeface(FontManager.getInstance().getUiMedium(context));
            binding.tvSha.setTypeface(FontManager.getInstance().getUiFont(context));
            binding.tvMessage.setTypeface(FontManager.getInstance().getUiSemiBold(context));
            binding.tvAuthorTime.setTypeface(FontManager.getInstance().getUiMedium(context));

            binding.tvSha.setText(item.getShortSha());
            binding.tvMessage.setText(item.getMessage());
            binding.tvAuthorTime.setText(String.format("%s • %s", item.getAuthor(), item.getTimestamp()));

            // HEAD Badge tracking layout logic: display badge only for the topmost tip commit
            binding.tvHeadBadge.setVisibility(isFirst ? View.VISIBLE : View.GONE);

            GradientDrawable nodeDrawable = new GradientDrawable();
            nodeDrawable.setShape(GradientDrawable.OVAL);
            int accent = ContextCompat.getColor(context, R.color.vcode_accent_primary);

            if (isFirst) {
                // Style tip commit nodes with a solid filled visual accent weight
                nodeDrawable.setColor(accent);
                binding.viewNode.setBackground(nodeDrawable);
            } else {
                // Style older chronological nodes with clear bordered stroke indicators
                nodeDrawable.setStroke(UiUtils.dpToPx(context, 2), accent);
                nodeDrawable.setColor(ContextCompat.getColor(context, android.R.color.transparent));
                binding.viewNode.setBackground(nodeDrawable);
            }

            // Timeline line connection rules: isolate terminal points to match list constraints smoothly
            binding.viewLineTop.setVisibility(isFirst ? View.INVISIBLE : View.VISIBLE);
            binding.viewLineBottom.setVisibility(isLast ? View.INVISIBLE : View.VISIBLE);

            binding.btnOverflow.setOnClickListener(v -> listener.onOverflowClick(item, v));
            binding.getRoot().setOnClickListener(v -> listener.onCommitClick(item));
        }
    }
}