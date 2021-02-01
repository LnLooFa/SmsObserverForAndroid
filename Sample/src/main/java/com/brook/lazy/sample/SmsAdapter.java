package com.brook.lazy.sample;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class SmsAdapter extends RecyclerView.Adapter<SmsAdapter.ViewHolder> {
    private List<String> mSmsLists=new ArrayList<>();

    public void setmSmsLists(String sms) {
        if(mSmsLists!=null){
            if(mSmsLists.size()>200){
                mSmsLists.remove(0);
            }
            mSmsLists.add(sms);
            notifyItemChanged(mSmsLists.size());
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_sms_content, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.getTextView().setText(mSmsLists.get(position));
    }

    @Override
    public int getItemCount() {
        return mSmsLists==null?0:mSmsLists.size();
    }

    /**
     * Provide a reference to the type of views that you are using
     * (custom ViewHolder).
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView textView;

        public ViewHolder(View view) {
            super(view);
            textView = (TextView) view.findViewById(R.id.tv_sms_content);
        }

        public TextView getTextView() {
            return textView;
        }
    }
}
