package com.vktech.qrandbarcodescannerapp;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA_PERMISSION = 10;
    private PreviewView previewView;
    private Executor executor;
    private boolean isScanning = false; // flag to prevent multiple dialogs

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.previewView);
        executor = ContextCompat.getMainExecutor(this);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Preview use case
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // Barcode scanner from ML Kit
                BarcodeScanner scanner = BarcodeScanning.getClient();

                // Image analysis use case
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(executor, imageProxy -> {
                    scanBarcodes(scanner, imageProxy);
                });

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                Log.e("QRScanner", "Error starting camera: " + e.getMessage());
            }
        }, executor);
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void scanBarcodes(BarcodeScanner scanner, ImageProxy imageProxy) {
        if (isScanning) {
            imageProxy.close();
            return;
        }

        android.media.Image mediaImage = imageProxy.getImage();
        if (mediaImage != null) {
            InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
            scanner.process(image)
                    .addOnSuccessListener(barcodes -> {
                        if (!barcodes.isEmpty()) {
                            isScanning = true; // prevent new scans while handling

                            for (Barcode barcode : barcodes) {
                                String rawValue = barcode.getRawValue();

                                runOnUiThread(() -> {
                                    Toast.makeText(this, "Scanned: " + rawValue, Toast.LENGTH_SHORT).show();

                                    if (rawValue != null && (rawValue.startsWith("http://") || rawValue.startsWith("https://"))) {
                                        showOpenUrlDialog(rawValue);
                                    } else {
                                        showTextDialog(rawValue);
                                    }
                                });
                                break; // only handle first barcode
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e("QRScanner", "Scan failed: " + e.getMessage());
                    })
                    .addOnCompleteListener(task -> {
                        imageProxy.close();
                        // allow new scans after user handles dialog
                        // this is done inside dialog positive/negative buttons
                    });
        } else {
            imageProxy.close();
        }
    }

    private void showOpenUrlDialog(String url) {
        new AlertDialog.Builder(this)
                .setTitle("Open URL?")
                .setMessage(url)
                .setPositiveButton("Yes", (dialog, which) -> {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(browserIntent);
                    isScanning = false; // allow next scan
                })
                .setNegativeButton("No", (dialog, which) -> {
                    isScanning = false; // allow next scan
                    dialog.dismiss();
                })
                .setOnDismissListener(dialog -> isScanning = false) // safety
                .show();
    }

    private void showTextDialog(String text) {
        new AlertDialog.Builder(this)
                .setTitle("Scanned Text")
                .setMessage(text)
                .setPositiveButton("OK", (dialog, which) -> {
                    isScanning = false; // allow next scan
                })
                .setOnDismissListener(dialog -> isScanning = false) // safety
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 &&
                    grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission is required to scan", Toast.LENGTH_SHORT).show();
            }
        }
    }
}