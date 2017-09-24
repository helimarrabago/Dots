package capstone.dots;

import android.app.Activity;
import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Created by lenovo on 9/24/2017.
 */

public class GridViewAdapter extends ArrayAdapter {
    private Context context;
    private int layoutResourceId;
    private ArrayList data = new ArrayList();

    public GridViewAdapter(Context context, int layoutResourceId, ArrayList data) {
        super(context, layoutResourceId, data);
        this.layoutResourceId = layoutResourceId;
        this.context = context;
        this.data = data;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View row = convertView;
        ViewHolder holder = null;

        if (row == null) {
            LayoutInflater inflater = ((Activity) context).getLayoutInflater();
            row = inflater.inflate(layoutResourceId, parent, false);
            holder = new ViewHolder();
            holder.date = row.findViewById(R.id.date);
            holder.thumbnail = row.findViewById(R.id.thumbnail);
            row.setTag(holder);
        } else holder  = (ViewHolder) row.getTag();

        ImageItem item = (ImageItem) data.get(position);
        holder.date.setText(item.getDate());
        holder.thumbnail.setImageBitmap(item.getThumbnail());

        return row;
    }

    static class ViewHolder {
        TextView date;
        ImageView thumbnail;
    }
}
