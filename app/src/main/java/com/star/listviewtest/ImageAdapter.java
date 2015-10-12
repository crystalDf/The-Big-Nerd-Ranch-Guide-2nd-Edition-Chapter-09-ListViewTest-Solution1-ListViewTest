package com.star.listviewtest;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;

import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class ImageAdapter extends ArrayAdapter<String> {

    private LruCache<String, BitmapDrawable> mMemoryCache;

    private ListView mListView;

    public ImageAdapter(Context context, int resource, String[] objects) {
        super(context, resource, objects);

        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = maxMemory / 8;

        mMemoryCache = new LruCache<String, BitmapDrawable>(cacheSize) {
            @Override
            protected int sizeOf(String key, BitmapDrawable bitmapDrawable) {
                return bitmapDrawable.getBitmap().getByteCount() / 1024;
            }
        };
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        if (mListView == null) {
            mListView = (ListView) parent;
        }

        String url = getItem(position);

        View view;

        if (convertView == null) {
            view = LayoutInflater.from(getContext()).inflate(R.layout.image_item, null);
        } else {
            view = convertView;
        }

        ImageView imageView = (ImageView) view.findViewById(R.id.image_view);
        imageView.setImageResource(R.drawable.empty_photo);
        imageView.setTag(url);

        BitmapDrawable bitmapDrawable = getBitmapFromMemoryCache(url);

        if (bitmapDrawable != null) {
            imageView.setImageDrawable(bitmapDrawable);
        } else {
            BitmapWorkerTask bitmapWorkerTask = new BitmapWorkerTask();
            bitmapWorkerTask.execute(url);
        }

        return view;
    }

    private BitmapDrawable getBitmapFromMemoryCache(String key) {
        return mMemoryCache.get(key);
    }

    private void addBitmapToMemoryCache(String key, BitmapDrawable bitmapDrawable) {
        mMemoryCache.put(key, bitmapDrawable);
    }

    private class BitmapWorkerTask extends AsyncTask<String, Void, BitmapDrawable> {

        private ImageView mImageView;
        private String mUrl;

        @Override
        protected BitmapDrawable doInBackground(String... params) {

            mUrl = params[0];

            Bitmap bitmap = downloadBitmap(mUrl);

            BitmapDrawable bitmapDrawable = new BitmapDrawable(getContext().getResources(), bitmap);

            addBitmapToMemoryCache(mUrl, bitmapDrawable);

            return bitmapDrawable;
        }

        @Override
        protected void onPostExecute(BitmapDrawable drawable) {

            mImageView = (ImageView) mListView.findViewWithTag(mUrl);

            if (mImageView != null && drawable != null) {
                mImageView.setImageDrawable(drawable);
            }
        }

        private Bitmap downloadBitmap(String imageUrl) {
            Bitmap bitmap = null;

            try {

                URL url = new URL(imageUrl);

                bitmap = decodeSampledBitmapFromStream(url, 300, 100);

            } catch (MalformedURLException e) {
                e.printStackTrace();
            }

            return bitmap;
        }

        private Bitmap decodeSampledBitmapFromStream(URL url,
                                                     int requiredWidth, int requiredHeight) {

            HttpURLConnection httpURLConnection = null;

            try {

                httpURLConnection = (HttpURLConnection) url.openConnection();
                httpURLConnection.setConnectTimeout(5 * 1000);
                httpURLConnection.setReadTimeout(10 * 1000);

                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                IOUtils.copy(httpURLConnection.getInputStream(), byteArrayOutputStream);
                byte[] bytes = byteArrayOutputStream.toByteArray();

                final BitmapFactory.Options options = new BitmapFactory.Options();

                options.inJustDecodeBounds = true;

                BitmapFactory.decodeStream(new ByteArrayInputStream(bytes), null, options);

                options.inSampleSize = calculateInSampleSize(options, requiredWidth, requiredHeight);

                options.inJustDecodeBounds = false;

                return BitmapFactory.decodeStream(new ByteArrayInputStream(bytes), null, options);

            } catch (IOException e) {
                e.printStackTrace();
                return null;
            } finally {
                if (httpURLConnection != null) {
                    httpURLConnection.disconnect();
                }
            }

        }

        private int calculateInSampleSize(BitmapFactory.Options options,
                                          int requiredWidth, int requiredHeight) {

            final int width = options.outWidth;
            final int height = options.outHeight;

            int inSampleSize = 1;

            if (width > requiredWidth || height > requiredHeight) {
                final int widthRatio = width / requiredWidth;
                final int heightRatio = height / requiredHeight;

                inSampleSize = Math.min(widthRatio, heightRatio);
            }

            return inSampleSize;
        }

    }
}
