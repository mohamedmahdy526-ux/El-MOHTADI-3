package android.print;

import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;

public class PrintDocumentAdapterHelper {
    public interface LayoutCallback {
        void onLayoutFinished(PrintDocumentInfo info, boolean changed);
        void onLayoutFailed(CharSequence error);
        void onLayoutCancelled();
    }

    public interface WriteCallback {
        void onWriteFinished(PageRange[] pages);
        void onWriteFailed(CharSequence error);
        void onWriteCancelled();
    }

    public static void runLayout(
            PrintDocumentAdapter adapter,
            PrintAttributes oldAttributes,
            PrintAttributes newAttributes,
            CancellationSignal cancellationSignal,
            final LayoutCallback layoutCallback,
            Bundle metadata
    ) {
        PrintDocumentAdapter.LayoutResultCallback sdkCallback = new PrintDocumentAdapter.LayoutResultCallback() {
            @Override
            public void onLayoutFinished(PrintDocumentInfo info, boolean changed) {
                if (layoutCallback != null) {
                    layoutCallback.onLayoutFinished(info, changed);
                }
            }

            @Override
            public void onLayoutFailed(CharSequence error) {
                if (layoutCallback != null) {
                    layoutCallback.onLayoutFailed(error);
                }
            }

            @Override
            public void onLayoutCancelled() {
                if (layoutCallback != null) {
                    layoutCallback.onLayoutCancelled();
                }
            }
        };

        adapter.onLayout(oldAttributes, newAttributes, cancellationSignal, sdkCallback, metadata);
    }

    public static void runWrite(
            PrintDocumentAdapter adapter,
            PageRange[] pages,
            ParcelFileDescriptor destination,
            CancellationSignal cancellationSignal,
            final WriteCallback writeCallback
    ) {
        PrintDocumentAdapter.WriteResultCallback sdkCallback = new PrintDocumentAdapter.WriteResultCallback() {
            @Override
            public void onWriteFinished(PageRange[] pages) {
                if (writeCallback != null) {
                    writeCallback.onWriteFinished(pages);
                }
            }

            @Override
            public void onWriteFailed(CharSequence error) {
                if (writeCallback != null) {
                    writeCallback.onWriteFailed(error);
                }
            }

            @Override
            public void onWriteCancelled() {
                if (writeCallback != null) {
                    writeCallback.onWriteCancelled();
                }
            }
        };

        adapter.onWrite(pages, destination, cancellationSignal, sdkCallback);
    }
}
