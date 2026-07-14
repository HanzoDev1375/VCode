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
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class GitStashFragment extends Fragment {

    private GitViewModel viewModel;
    private RecyclerView rvStashes;
    private StashAdapter adapter;
    private View layoutEmptyStashes;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_git_stash, container, false);
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

        view.findViewById(R.id.btn_create_stash).setOnClickListener(v -> viewModel.stashCreate());

        viewModel.getStashes().observe(getViewLifecycleOwner(), stashes -> {
            boolean empty = stashes == null || stashes.isEmpty();
            rvStashes.setVisibility(empty ? View.GONE : View.VISIBLE);
            layoutEmptyStashes.setVisibility(empty ? View.VISIBLE : View.GONE);
            if (!empty) adapter.updateData(stashes);
        });
    }

    private void setupTypefaces(View view) {
        FontManager fm = FontManager.getInstance();
        ((TextView) view.findViewById(R.id.tv_stash_title))
                .setTypeface(fm.getUiSemiBold(requireContext()));
        ((TextView) view.findViewById(R.id.tv_stash_subtitle))
                .setTypeface(fm.getUiMedium(requireContext()));
        ((MaterialButton) view.findViewById(R.id.btn_create_stash))
                .setTypeface(fm.getUiSemiBold(requireContext()));
        ((TextView) view.findViewById(R.id.tv_empty_stash_title))
                .setTypeface(fm.getUiSemiBold(requireContext()));
        ((TextView) view.findViewById(R.id.tv_empty_stash_desc))
                .setTypeface(fm.getUiMedium(requireContext()));
    }

    private class StashAdapter extends RecyclerView.Adapter<StashAdapter.VH> {
        private List<StashItem> items;

        StashAdapter(List<StashItem> items) {
            this.items = items;
        }

        void updateData(List<StashItem> data) {
            this.items = data;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_stash, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            StashItem item = items.get(pos);
            h.tvName.setText(item.getName());
            h.tvMsg.setText(item.getTimestamp() + " · " + item.getMessage());
            h.btnApply.setOnClickListener(v -> viewModel.stashApply(item.getId()));
            h.btnDrop.setOnClickListener(v -> viewModel.stashDrop(item.getId()));
        }

        @Override
        public int getItemCount() {
            return items != null ? items.size() : 0;
        }

        class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvMsg;
            ImageView btnApply, btnDrop;

            VH(@NonNull View v) {
                super(v);
                tvName = v.findViewById(R.id.tv_stash_name);
                tvMsg = v.findViewById(R.id.tv_stash_message);
                btnApply = v.findViewById(R.id.btn_apply_stash);
                btnDrop = v.findViewById(R.id.btn_drop_stash);
                FontManager fm = FontManager.getInstance();
                tvName.setTypeface(fm.getUiSemiBold(v.getContext()));
                tvMsg.setTypeface(fm.getUiMedium(v.getContext()));
            }
        }
    }
}
