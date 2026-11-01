package com.rescuelink.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.rescuelink.app.R;
import com.rescuelink.app.utils.UiUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Generic card row used by list screens (alerts, guides, contacts, users, stations). */
public class RowAdapter extends RecyclerView.Adapter<RowAdapter.VH> {
    public static class Row {
        public String title = "";
        public String sub = "";
        public String meta = "";
        public String badge;
        public int badgeFg;
        public int badgeBg;
        public int iconRes;
        public int iconBg;
        public Object tag;

        public Row(String title, String sub) {
            this.title = title;
            this.sub = sub;
        }

        public Row meta(String m) { meta = m; return this; }
        public Row badge(String text, int fg, int bg) { badge = text; badgeFg = fg; badgeBg = bg; return this; }
        public Row icon(int res, int bg) { iconRes = res; iconBg = bg; return this; }
        public Row tag(Object t) { tag = t; return this; }
    }

    private final List<Row> items = new ArrayList<>();
    private final Consumer<Row> onClick;

    public RowAdapter(Consumer<Row> onClick) { this.onClick = onClick; }

    public void setItems(List<Row> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_row, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Row r = items.get(position);
        h.title.setText(r.title);
        h.sub.setText(r.sub);
        h.sub.setVisibility(r.sub == null || r.sub.isEmpty() ? View.GONE : View.VISIBLE);
        if (r.meta == null || r.meta.isEmpty()) {
            h.meta.setVisibility(View.GONE);
        } else {
            h.meta.setText(r.meta);
            h.meta.setVisibility(View.VISIBLE);
        }
        if (r.badge == null) {
            h.badge.setVisibility(View.GONE);
        } else {
            UiUtils.styleBadge(h.badge, r.badge, r.badgeFg, r.badgeBg);
            h.badge.setVisibility(View.VISIBLE);
        }
        if (r.iconRes == 0) {
            h.iconFrame.setVisibility(View.GONE);
        } else {
            h.iconFrame.setVisibility(View.VISIBLE);
            h.iconFrame.setBackground(UiUtils.rounded(r.iconBg, UiUtils.dp(h.itemView.getContext(), 14)));
            h.icon.setImageResource(r.iconRes);
        }
        h.itemView.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                onClick.accept(r);
            }
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView title, sub, meta, badge;
        final FrameLayout iconFrame;
        final ImageView icon;

        VH(@NonNull View v) {
            super(v);
            title = v.findViewById(R.id.tv_row_title);
            sub = v.findViewById(R.id.tv_row_sub);
            meta = v.findViewById(R.id.tv_row_meta);
            badge = v.findViewById(R.id.tv_row_badge);
            iconFrame = v.findViewById(R.id.fl_row_icon);
            icon = v.findViewById(R.id.iv_row_icon);
        }
    }
}
