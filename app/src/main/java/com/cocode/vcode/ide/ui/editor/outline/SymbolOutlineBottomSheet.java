package com.cocode.vcode.ide.ui.editor.outline;

import android.os.Bundle;
import android.text.Layout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.model.FileType;
import com.cocode.vcode.ide.views.CodeEditText;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SymbolOutlineBottomSheet extends BottomSheetDialogFragment {

    private CodeEditText editor;
    private FileType fileType;

    public void setEditor(CodeEditText editor, FileType fileType) {
        this.editor = editor;
        this.fileType = fileType;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.vcode_bottom_sheet_outline, container, false);
        RecyclerView recycler = view.findViewById(R.id.rv_outline);
        
        List<SymbolModel> symbols = extractSymbols();
        
        recycler.setAdapter(new RecyclerView.Adapter<SymbolViewHolder>() {
            @NonNull
            @Override
            public SymbolViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View item = LayoutInflater.from(parent.getContext()).inflate(R.layout.vcode_item_symbol, parent, false);
                return new SymbolViewHolder(item);
            }

            @Override
            public void onBindViewHolder(@NonNull SymbolViewHolder holder, int position) {
                SymbolModel symbol = symbols.get(position);
                holder.name.setText(symbol.getName());
                holder.line.setText("Line " + symbol.getLineNumber());
                holder.icon.setImageResource(symbol.getIconResId());
                
                if (symbol.getDetails() != null && !symbol.getDetails().isEmpty()) {
                    holder.details.setVisibility(View.VISIBLE);
                    holder.details.setText(symbol.getDetails());
                } else {
                    holder.details.setVisibility(View.GONE);
                }
                
                holder.itemView.setOnClickListener(v -> {
                    if (editor != null && editor.getLayout() != null) {
                        int line = symbol.getLineNumber() - 1;
                        int offset = editor.getLayout().getLineStart(line);
                        editor.setSelection(offset);
                        // Optional: scroll to position could be handled by editor
                    }
                    dismiss();
                });
            }

            @Override
            public int getItemCount() {
                return symbols.size();
            }
        });
        
        return view;
    }

    private List<SymbolModel> extractSymbols() {
        List<SymbolModel> symbols = new ArrayList<>();
        if (editor == null || editor.getText() == null) return symbols;
        String text = editor.getText().toString();
        Layout layout = editor.getLayout();
        if (layout == null) return symbols;

        if (fileType == FileType.JAVASCRIPT || fileType == FileType.TYPESCRIPT) {
            Pattern p = Pattern.compile("(?m)^(?:\\s*export\\s+)?(?:\\s*async\\s+)?\\s*(?:function\\s+([a-zA-Z_$][\\w$]*)|(?:class|interface|type)\\s+([a-zA-Z_$][\\w$]*)|(?:const|let|var)\\s+([a-zA-Z_$][\\w$]*)\\s*=\\s*(?:async\\s*)?(?:function|\\([^)]*\\)\\s*=>|[a-zA-Z_$][\\w$]*\\s*=>))");
            Matcher m = p.matcher(text);
            while (m.find()) {
                String name = m.group(1);
                if (name == null) name = m.group(2);
                if (name == null) name = m.group(3);
                if (name != null) {
                    int line = layout.getLineForOffset(m.start()) + 1;
                    int icon = text.substring(m.start(), m.end()).contains("class") ? R.drawable.ic_code : R.drawable.ic_file_code;
                    symbols.add(new SymbolModel(name, "", line, icon));
                }
            }
        } else if (fileType == FileType.HTML) {
            Pattern p = Pattern.compile("(?i)<(h[1-6]|div|section|article|nav|header|footer)[^>]*?(?:id=[\"']([^\"']+)[\"'])?[^>]*?>");
            Matcher m = p.matcher(text);
            while (m.find()) {
                String tag = m.group(1);
                String id = m.group(2);
                int line = layout.getLineForOffset(m.start()) + 1;
                String details = tag != null ? "<" + tag + ">" : "";
                String name = id != null ? "#" + id : (tag != null ? tag : "Element");
                // Skip plain divs without ids
                if (id == null && tag != null && tag.toLowerCase().equals("div")) continue;
                symbols.add(new SymbolModel(name, details, line, R.drawable.ic_html_icon));
            }
        } else if (fileType == FileType.CSS || fileType == FileType.SCSS) {
            Pattern p = Pattern.compile("(?m)^\\s*([a-zA-Z0-9_.#:&\\-\\[\\]=~|^$*+> ,]+)\\s*\\{");
            Matcher m = p.matcher(text);
            while (m.find()) {
                String name = m.group(1).trim();
                int line = layout.getLineForOffset(m.start()) + 1;
                symbols.add(new SymbolModel(name, "", line, R.drawable.ic_css_icon));
            }
        } else if (fileType == FileType.MARKDOWN) {
            Pattern p = Pattern.compile("(?m)^\\s*(#{1,6})\\s+(.+)$");
            Matcher m = p.matcher(text);
            while (m.find()) {
                String hashes = m.group(1);
                String name = m.group(2).trim();
                int line = layout.getLineForOffset(m.start()) + 1;
                symbols.add(new SymbolModel(name, "H" + hashes.length(), line, R.drawable.ic_md_icon));
            }
        }

        return symbols;
    }

    static class SymbolViewHolder extends RecyclerView.ViewHolder {
        TextView name, details, line;
        ImageView icon;

        public SymbolViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tv_name);
            details = itemView.findViewById(R.id.tv_details);
            line = itemView.findViewById(R.id.tv_line);
            icon = itemView.findViewById(R.id.iv_icon);
        }
    }
}
