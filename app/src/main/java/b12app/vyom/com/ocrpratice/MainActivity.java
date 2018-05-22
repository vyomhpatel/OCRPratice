package b12app.vyom.com.ocrpratice;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.camera2.CameraAccessException;
import android.net.Uri;
import android.os.Environment;
import android.os.PersistableBundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.Text;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;

import java.io.File;
import java.io.FileNotFoundException;

import b12app.vyom.com.ocrpratice.ocrutil.CameraSourcePreview;
import b12app.vyom.com.ocrpratice.ocrutil.GraphicOverlay;
import b12app.vyom.com.ocrpratice.ocrutil.OcrGraphic;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {


    public static final int REQUEST_CODE = 1;
    private static final int PHOTO_REQUEST = 10;
    public static final String PERMISSION_STRING = "Access to the camera is needed for detection";
    public static final String DETECTOR_ERR = "Could not set up the detector!";
    private boolean autoFocus = true;
    private boolean usesFlash = false;
    private int check_permission;

    private Uri imageUri;
    private TextRecognizer detector;
    private static final String SAVED_INSTANCE_URI = "uri";
    private static final String SAVED_INSTANCE_RESULT = "result";
    private Button button;
    private TextView scanResults, results;
    private ImageView imageView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();


        if (savedInstanceState != null) {
            imageUri = Uri.parse(savedInstanceState.getString(SAVED_INSTANCE_URI));
            scanResults.setText(savedInstanceState.getString(SAVED_INSTANCE_RESULT));
        }

        button.setOnClickListener(this);
        detector = new TextRecognizer.Builder(getApplicationContext()).build();
    }

    private void initViews() {
        button = findViewById(R.id.button);
        scanResults = findViewById(R.id.textView);
        results = findViewById(R.id.results);
        imageView = findViewById(R.id.imageView);

    }

    @Override
    public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
        if (imageUri != null) {
            outState.putString(SAVED_INSTANCE_URI, imageUri.toString());
            outState.putString(SAVED_INSTANCE_RESULT, scanResults.getText().toString());
        }
        super.onSaveInstanceState(outState, outPersistentState);
    }

    private void requestPermission() {

            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CODE);


    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    captureImage();
                } else {
                    Toast.makeText(MainActivity.this, "Permission Denied!", Toast.LENGTH_SHORT).show();
                }

        }
    }

    private void captureImage() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File photo = new File(Environment.getExternalStorageDirectory(), "picture.jpg");
        imageUri = FileProvider.getUriForFile(MainActivity.this,
                BuildConfig.APPLICATION_ID + ".provider", photo);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
        startActivityForResult(intent, PHOTO_REQUEST);
    }

    private void launchMediaScanIntent() {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        mediaScanIntent.setData(imageUri);
        this.sendBroadcast(mediaScanIntent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        String blocks = "";
        String lines = "";
        String words = "";

        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PHOTO_REQUEST && resultCode == RESULT_OK) {
            launchMediaScanIntent();

            try {
                Bitmap bitmap = decodeBitmapUri(this, imageUri);
                if(detector.isOperational() && bitmap!=null){
                    Frame frame = new Frame.Builder().setBitmap(bitmap).build();
                    SparseArray<TextBlock> textBlockSparseArray = detector.detect(frame);
                    for(int index=0; index < textBlockSparseArray.size(); index++){
                        TextBlock textBlock = textBlockSparseArray.valueAt(index);
                        blocks += textBlock.getValue()+"\n"+"\n";
                        for(Text line: textBlock.getComponents()){
                            lines += line.getValue()+"\n";
                            for(Text word: line.getComponents()){
                                words += word.getValue()+", ";
                            }
                        }
                    }
                    if (textBlockSparseArray.size() == 0) {
                        scanResults.setText("Scan Failed: Found nothing to scan");
                    } else {
                        scanResults.setText(scanResults.getText() + "Blocks: " + "\n");
                        scanResults.setText(scanResults.getText() + blocks + "\n");
                        scanResults.setText(scanResults.getText() + "---------" + "\n");
                        scanResults.setText(scanResults.getText() + "Lines: " + "\n");
                        scanResults.setText(scanResults.getText() + lines + "\n");
                        scanResults.setText(scanResults.getText() + "---------" + "\n");
                        scanResults.setText(scanResults.getText() + "Words: " + "\n");
                        scanResults.setText(scanResults.getText() + words + "\n");
                        scanResults.setText(scanResults.getText() + "---------" + "\n");
                    }
                }
                else {
                    scanResults.setText(DETECTOR_ERR);
                }

            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    private Bitmap decodeBitmapUri(Context ctx, Uri uri) throws FileNotFoundException {
        int targetW = 600;
        int targetH = 600;
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(ctx.getContentResolver().openInputStream(uri), null, bmOptions);
        int photoW = bmOptions.outWidth;
        int photoH = bmOptions.outHeight;

        int scaleFactor = Math.min(photoW / targetW, photoH / targetH);
        bmOptions.inJustDecodeBounds = false;
        bmOptions.inSampleSize = scaleFactor;

        return BitmapFactory.decodeStream(ctx.getContentResolver()
                .openInputStream(uri), null, bmOptions);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.button:
                check_permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);

                if (check_permission == PackageManager.PERMISSION_GRANTED) {
                        captureImage();
                } else {

                    requestPermission();
                }
                break;
        }
    }
}
