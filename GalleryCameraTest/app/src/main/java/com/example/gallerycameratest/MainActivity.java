package com.example.gallerycameratest;

import android.app.Activity;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private static final int PICK_IMAGE = 1001;
    private boolean captureRequest = false;
    private Uri outputUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent launch = getIntent();
        String action = launch != null ? launch.getAction() : null;
        captureRequest = MediaStore.ACTION_IMAGE_CAPTURE.equals(action)
                || MediaStore.ACTION_IMAGE_CAPTURE_SECURE.equals(action);

        if (launch != null) {
            Object extra = launch.getParcelableExtra(MediaStore.EXTRA_OUTPUT);
            if (extra instanceof Uri) outputUri = (Uri) extra;
        }

        if (captureRequest) {
            showCaptureScreen();
            openGallery();
        } else {
            showHomeScreen();
        }
    }

    private void showHomeScreen() {
        LinearLayout root = baseLayout();

        TextView title = new TextView(this);
        title.setText("Gallery Camera Test");
        title.setTextSize(24f);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(18));
        root.addView(title, matchWrap());

        TextView info = new TextView(this);
        info.setText("App di prova: quando un'altra app usa il comando Android standard Scatta foto, puoi scegliere questa app e selezionare una foto dalla galleria.");
        info.setTextSize(16f);
        info.setPadding(0, 0, 0, dp(22));
        root.addView(info, matchWrap());

        Button test = new Button(this);
        test.setText("PROVA: SCEGLI FOTO DALLA GALLERIA");
        test.setOnClickListener(v -> {
            captureRequest = false;
            openGallery();
        });
        root.addView(test, matchWrap());

        setContentView(root);
    }

    private void showCaptureScreen() {
        LinearLayout root = baseLayout();
        TextView title = new TextView(this);
        title.setText("Scegli una foto dalla galleria");
        title.setTextSize(22f);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView info = new TextView(this);
        info.setText("La foto selezionata verrà restituita all'app che ha richiesto lo scatto.");
        info.setTextSize(16f);
        info.setPadding(0, dp(12), 0, dp(20));
        root.addView(info, matchWrap());

        Button choose = new Button(this);
        choose.setText("APRI GALLERIA");
        choose.setOnClickListener(v -> openGallery());
        root.addView(choose, matchWrap());

        Button cancel = new Button(this);
        cancel.setText("ANNULLA");
        cancel.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
        root.addView(cancel, matchWrap());

        setContentView(root);
    }

    private LinearLayout baseLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(44), dp(24), dp(24));
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        return root;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void openGallery() {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("image/*");
        startActivityForResult(pick, PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_IMAGE) return;

        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            if (captureRequest) {
                setResult(RESULT_CANCELED);
                finish();
            }
            return;
        }

        Uri selected = data.getData();
        if (!captureRequest) {
            Toast.makeText(this, "Foto selezionata correttamente", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Bitmap bitmap = decodeScaled(selected, 2400);
            if (bitmap == null) throw new IOException("Immagine non leggibile");

            if (outputUri != null) {
                writeJpeg(bitmap, outputUri);
                Intent result = new Intent();
                result.setData(outputUri);
                result.setClipData(ClipData.newRawUri("photo", outputUri));
                result.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                setResult(RESULT_OK, result);
            } else {
                Bitmap thumb = scaleDown(bitmap, 512);
                Intent result = new Intent();
                result.putExtra("data", thumb);
                setResult(RESULT_OK, result);
            }
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "Errore: " + e.getMessage(), Toast.LENGTH_LONG).show();
            setResult(RESULT_CANCELED);
            finish();
        }
    }

    private Bitmap decodeScaled(Uri uri, int maxSide) throws IOException {
        ContentResolver resolver = getContentResolver();
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = resolver.openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }

        int sample = 1;
        int largest = Math.max(bounds.outWidth, bounds.outHeight);
        while (largest / sample > maxSide * 2) sample *= 2;

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        try (InputStream in = resolver.openInputStream(uri)) {
            Bitmap bmp = BitmapFactory.decodeStream(in, null, opts);
            return bmp == null ? null : scaleDown(bmp, maxSide);
        }
    }

    private Bitmap scaleDown(Bitmap src, int maxSide) {
        int w = src.getWidth();
        int h = src.getHeight();
        int largest = Math.max(w, h);
        if (largest <= maxSide) return src;
        float ratio = maxSide / (float) largest;
        int nw = Math.max(1, Math.round(w * ratio));
        int nh = Math.max(1, Math.round(h * ratio));
        return Bitmap.createScaledBitmap(src, nw, nh, true);
    }

    private void writeJpeg(Bitmap bitmap, Uri destination) throws IOException {
        ContentResolver resolver = getContentResolver();
        try (OutputStream out = resolver.openOutputStream(destination, "w")) {
            if (out == null) throw new IOException("Destinazione non scrivibile");
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)) {
                throw new IOException("Errore durante la conversione JPEG");
            }
            out.flush();
        }
    }
}
