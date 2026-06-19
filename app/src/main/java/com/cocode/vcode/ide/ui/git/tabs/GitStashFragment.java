package com.cocode.vcode.ide.ui.git.tabs;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.git.model.StashItem;
import com.cocode.vcode.ide.ui.git.GitViewModel;
import com.cocode.vcode.ide.utils.FontManager;

import java.util.ArrayList;
import java.util.List;

public class GitStashFragment extends Fragment {

    private GitViewModel viewModel;
    private View rootView;
    private RecyclerView rvStashes;
    private StashAdapter adapter;
    private View layoutEmptyStashes;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_git_stash, container, false);
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(GitViewModel.class);

        rvStashes = view.findViewById(R.id.rv_stashes);
        layoutEmptyStashes = view.findViewById(R.id.layout_empty_stashes);

        rvStashes.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new StashAdapter(new ArrayList<>());
        rvStashes.setAdapter(adapter);

        setupTypefaces(view);

        view.findViewById(R.id.btn_create_stash).setOnClickListener(v -> {
            viewModel.stashCreate();
        });

        viewModel.getStashes().observe(getViewLifecycleOwner(), stashes -> {
            if (stashes == null || stashes.isEmpty()) {
                rvStashes.setVisibility(View.GONE);
                layoutEmptyStashes.setVisibility(View.VISIBLE);
            } else {
                rvStashes.setVisibility(View.VISIBLE);
                layoutEmptyStashes.setVisibility(View.GONE);
                adapter.updateData(stashes);
            }
        });
    }

    private void setupTypefaces(View view) {
        TextView tvTitle = view.findViewById(R.id.tv_empty_stash_title);
        TextView tvDesc = view.findViewById(R.id.tv_empty_stash_desc);
        com.google.android.material.button.MaterialButton btnCreate = view.findViewById(R.id.btn_create_stash);

        tvTitle.setTypeface(FontManager.getInstance().getUiSemiBold(requireContext()));
        tvDesc.setTypeface(FontManager.getInstance().getUiMedium(requireContext()));
        btnCreate.setTypeface(FontManager.getInstance().getUiSemiBold(requireContext()));
    }

    private class StashAdapter extends RecyclerView.Adapter<StashAdapter.ViewHolder> {
        private List<StashItem> items;

        public StashAdapter(List<StashItem> items) {
            this.items = items;
        }

        public void updateData(List<StashItem> newItems) {
            this.items = newItems;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.vcode_item_stash, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            StashItem item = items.get(position);
            holder.tvStashName.setText(item.getName());
            holder.tvStashMessage.setText(item.getMessage() + " (" + item.getTimestamp() + ")");

            holder.btnApply.setOnClickListener(v -> viewModel.stashApply(item.getId()));
            holder.btnDrop.setOnClickListener(v -> viewModel.stashDrop(item.getId()));
        }

        @Override
        public int getItemCount() {
            return items != null ? items.size() : 0;
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvStashName;
            TextView tvStashMessage;
            ImageView btnApply;
            ImageView btnDrop;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvStashName = itemView.findViewById(R.id.tv_stash_name);
                tvStashMessage = itemView.findViewById(R.id.tv_stash_message);
                btnApply = itemView.findViewById(R.id.btn_apply_stash);
                btnDrop = itemView.findViewById(R.id.btn_drop_stash);

                tvStashName.setTypeface(FontManager.getInstance().getUiSemiBold(itemView.getContext()));
                tvStashMessage.setTypeface(FontManager.getInstance().getUiMedium(itemView.getContext()));
            }
        }
    }
}
