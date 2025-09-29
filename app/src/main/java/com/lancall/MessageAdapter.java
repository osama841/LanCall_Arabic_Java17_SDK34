package com.lancall;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * RecyclerView adapter for displaying messages
 */
public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {
    private List<Message> messages = new ArrayList<>();
    private String localIpAddress;

    public MessageAdapter(String localIpAddress) {
        this.localIpAddress = localIpAddress;
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.message_item, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        Message message = messages.get(position);

        holder.senderIpText.setText(message.getSenderIp());
        holder.messageText.setText(message.getText());

        // Format timestamp
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        String formattedTime = sdf.format(new Date(message.getTimestamp()));
        holder.timestampText.setText(formattedTime);

        // Highlight messages from local user
        if (message.getSenderIp().equals(localIpAddress)) {
            holder.senderIpText.setText("أنت");
        }

        // Show message status
        switch (message.getStatus()) {
            case SENDING:
                holder.statusText.setText("جاري الإرسال...");
                holder.statusText.setVisibility(View.VISIBLE);
                break;
            case SENT:
                holder.statusText.setText("تم الإرسال");
                holder.statusText.setVisibility(View.VISIBLE);
                break;
            case DELIVERED:
                holder.statusText.setText("تم التوصيل");
                holder.statusText.setVisibility(View.VISIBLE);
                break;
            case FAILED:
                holder.statusText.setText("فشل الإرسال");
                holder.statusText.setVisibility(View.VISIBLE);
                break;
            default:
                holder.statusText.setVisibility(View.GONE);
                break;
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public void addMessage(Message message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public void updateMessageStatus(String messageId, Message.MessageStatus status) {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).getId().equals(messageId)) {
                messages.get(i).setStatus(status);
                notifyItemChanged(i);
                break;
            }
        }
    }

    public void clearMessages() {
        int size = messages.size();
        messages.clear();
        notifyItemRangeRemoved(0, size);
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView senderIpText;
        TextView messageText;
        TextView timestampText;
        TextView statusText;

        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            senderIpText = itemView.findViewById(R.id.senderIpText);
            messageText = itemView.findViewById(R.id.messageText);
            timestampText = itemView.findViewById(R.id.timestampText);
            statusText = itemView.findViewById(R.id.statusText);
        }
    }
}