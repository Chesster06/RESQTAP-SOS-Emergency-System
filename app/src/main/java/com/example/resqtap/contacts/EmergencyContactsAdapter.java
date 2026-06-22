package com.example.resqtap.contacts;

import com.example.resqtap.R;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Locale;

/**
 * EmergencyContactsAdapter
 * Adapter RecyclerView untuk item emergency contact (Name, Category badge, Phone, Notes, Message, Call, Edit).
 */
public class EmergencyContactsAdapter extends RecyclerView.Adapter<EmergencyContactsAdapter.VH> {
    public interface Listener {
        void onCall(EmergencyContact c);
        void onMessage(EmergencyContact c);
        void onEdit(EmergencyContact c);
        void onDelete(EmergencyContact c);
    }

    private final ArrayList<EmergencyContact> items = new ArrayList<>();
    private final Listener listener;

    public EmergencyContactsAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(ArrayList<EmergencyContact> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_emergency_contact, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        EmergencyContact c = items.get(position);
        Context ctx = h.itemView.getContext();
        String name = c == null ? "" : c.name;
        String rel = c == null ? "" : c.relationship;
        String phone = c == null ? "" : c.phone;
        String notes = c == null ? "" : c.notes;

        h.name.setText(name.isEmpty() ? "-" : name);

        // Category Badge styling
        if (rel == null || rel.trim().isEmpty()) {
            h.relationship.setVisibility(View.GONE);
        } else {
            h.relationship.setVisibility(View.VISIBLE);
            h.relationship.setText(rel);
            applyCategoryBadgeStyle(ctx, h.relationship, rel);
        }

        h.phone.setText(phone.isEmpty() ? "-" : phone);

        // Notes
        if (notes == null || notes.trim().isEmpty()) {
            h.notes.setVisibility(View.GONE);
        } else {
            h.notes.setVisibility(View.VISIBLE);
            h.notes.setText(notes);
        }

        // Action Click Listeners
        h.btnCall.setOnClickListener(v -> {
            if (listener != null && c != null) listener.onCall(c);
        });

        h.btnMessage.setOnClickListener(v -> {
            if (listener != null && c != null) listener.onMessage(c);
        });

        h.btnEdit.setOnClickListener(v -> {
            if (listener != null && c != null) listener.onEdit(c);
        });
    }

    private void applyCategoryBadgeStyle(Context ctx, TextView badge, String rel) {
        String lower = rel.toLowerCase(Locale.ROOT);
        if (lower.contains("doctor") || lower.contains("hospital") || lower.contains("medical") || lower.contains("clinic")) {
            badge.setBackground(ContextCompat.getDrawable(ctx, R.drawable.bg_badge_doctor));
            badge.setTextColor(0xFF1A73E8);
        } else if (lower.contains("babysitter") || lower.contains("nanny") || lower.contains("caregiver") || lower.contains("guardian") || lower.contains("penjaga")) {
            badge.setBackground(ContextCompat.getDrawable(ctx, R.drawable.bg_badge_babysitter));
            badge.setTextColor(0xFF137333);
        } else if (lower.contains("school") || lower.contains("teacher") || lower.contains("daycare") || lower.contains("college") || lower.contains("work") || lower.contains("colleague") || lower.contains("coworker") || lower.contains("sekerja")) {
            badge.setBackground(ContextCompat.getDrawable(ctx, R.drawable.bg_badge_school));
            badge.setTextColor(0xFF00796B);
        } else if (lower.contains("family") || lower.contains("parent") || lower.contains("mother") || lower.contains("father")
                || lower.contains("spouse") || lower.contains("wife") || lower.contains("husband") || lower.contains("sibling")
                || lower.contains("sister") || lower.contains("brother") || lower.contains("child") || lower.contains("son") || lower.contains("daughter")
                || lower.contains("partner") || lower.contains("keluarga") || lower.contains("ibu") || lower.contains("bapa")
                || lower.contains("suami") || lower.contains("isteri") || lower.contains("anak") || lower.contains("adik")
                || lower.contains("saudara") || lower.contains("pasangan")) {
            badge.setBackground(ContextCompat.getDrawable(ctx, R.drawable.bg_badge_family));
            badge.setTextColor(0xFF7C3AED);
        } else if (lower.contains("friend") || lower.contains("rakan") || lower.contains("teman") || lower.contains("neighbor") || lower.contains("jiran")) {
            badge.setBackground(ContextCompat.getDrawable(ctx, R.drawable.bg_badge_friend));
            badge.setTextColor(0xFFB45309);
        } else {
            badge.setBackground(ContextCompat.getDrawable(ctx, R.drawable.bg_badge_default));
            badge.setTextColor(0xFF374151);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class VH extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView relationship;
        final TextView phone;
        final TextView notes;
        final ImageButton btnEdit;
        final MaterialButton btnMessage;
        final MaterialButton btnCall;

        VH(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tv_contact_name);
            relationship = itemView.findViewById(R.id.tv_contact_relationship);
            phone = itemView.findViewById(R.id.tv_contact_phone);
            notes = itemView.findViewById(R.id.tv_contact_notes);
            btnEdit = itemView.findViewById(R.id.btn_edit);
            btnMessage = itemView.findViewById(R.id.btn_message);
            btnCall = itemView.findViewById(R.id.btn_call);
        }
    }
}
