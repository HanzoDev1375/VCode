package com.cocode.vcode.ide.git.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.cocode.vcode.ide.databinding.ItemBranchBinding;
import com.cocode.vcode.ide.git.model.BranchItem;
import com.cocode.vcode.ide.utils.FontManager;

/**
 * RecyclerView ListAdapter designed to display repository branches.
 * Animates item state updates efficiently via DiffUtil and formats branch items
 * with dedicated selection indicators and truncated commit descriptions.
 */
public class BranchAdapter extends ListAdapter<BranchItem, BranchAdapter.ViewHolder> {

    private final BranchListener listener;

    public BranchAdapter(BranchListener listener) {
        super(new DiffUtil.ItemCallback<>() {
            @Override
            public boolean areItemsTheSame(@NonNull BranchItem old, @NonNull BranchItem n) {
                return old.getName().equals(n.getName());
            }

            @Override
            public boolean areContentsTheSame(@NonNull BranchItem old, @NonNull BranchItem n) {
                return old.isActive() == n.isActive() &&
                        old.getLastCommit().equals(n.getLastCommit());
            }
        });
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemBranchBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    /**
     * Communications interface delivering interaction events back to the hosting view layer.
     */
    public interface BranchListener {
        void onBranchClick(BranchItem item);

        void onOverflowClick(BranchItem item, View anchor);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemBranchBinding binding;

        ViewHolder(ItemBranchBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(BranchItem item) {
            Context context = itemView.getContext();

            // Apply customized interface fonts to preserve typography continuity
            binding.tvBranchName.setTypeface(FontManager.getInstance().getUiSemiBold(context));
            binding.tvLastCommit.setTypeface(FontManager.getInstance().getUiMedium(context));
            binding.tvBranchName.setText(item.getName());

            // Truncate long commit messages gracefully if they extend beyond layout allowances
            String commitMsg = item.getLastCommit();
            if (commitMsg != null && commitMsg.length() > 30) {
                commitMsg = commitMsg.substring(0, 27) + "...";
            }
            binding.tvLastCommit.setText(commitMsg);

            // Toggle visibility of the vertical visual strip marking the currently checked-out branch
            binding.viewActiveStrip.setVisibility(item.isActive() ? View.VISIBLE : View.GONE);

            // Bind interaction handlers
            binding.getRoot().setOnClickListener(v -> listener.onBranchClick(item));
            binding.btnOverflow.setOnClickListener(v -> listener.onOverflowClick(item, v));
        }
    }
}