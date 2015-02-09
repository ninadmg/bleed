package in.ninad.p2c;

import android.app.IntentService;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Pattern;

import microsoft.aspnet.signalr.client.Action;
import microsoft.aspnet.signalr.client.ErrorCallback;
import microsoft.aspnet.signalr.client.LogLevel;
import microsoft.aspnet.signalr.client.Logger;
import microsoft.aspnet.signalr.client.hubs.HubConnection;
import microsoft.aspnet.signalr.client.hubs.HubProxy;


public class PostService extends IntentService {

    private static final String ACTION_TEXT = "in.ninad.p2c.action.TEXT";
    private static final String ACTION_IMAGE = "in.ninad.p2c.action.IMAGE";


    private static final String QRCODE = "in.ninad.p2c.extra.QRcode";
    private static final String EXTRA_PARAM = "in.ninad.p2c.extra.PARAM";

    private static final int CHUNK_SIZE = 262144;
    HubConnection conn;
    HubProxy hubProxy;
    Logger logger;
    Intent mIntent;
    private NotificationManager notificationManager;
    private NotificationCompat.Builder notification;
    private PowerManager.WakeLock wakeLock;
    private int lastPublishedProgress;

    public static void startActionText(Context context, String param1, String param2) {
        Intent intent = new Intent(context, PostService.class);
        intent.setAction(ACTION_TEXT);
        intent.putExtra(QRCODE, param1);
        intent.putExtra(EXTRA_PARAM, param2);
        context.startService(intent);
    }


    public static void startActionImage(Context context, String param1, String param2) {
        Intent intent = new Intent(context, PostService.class);
        intent.setAction(ACTION_IMAGE);
        intent.putExtra(QRCODE, param1);
        intent.putExtra(EXTRA_PARAM, param2);
        context.startService(intent);
    }

    public PostService() {
        super("PostService");
        logger = new Logger() {

            @Override
            public void log(String message, LogLevel level) {
                Log.e("ninad",message);
            }
        };

        conn = new HubConnection("http://go.mathewjibin.com/bleed","",true,logger);
      //  conn = new HubConnection(" http://192.168.2.101/takemethere/bleed","",true,logger);

    }

    @Override
    protected void onHandleIntent( Intent intent) {
        if (intent != null) {
            mIntent = intent;
            makeConnection();
        }
        wakeLock.acquire();
    }

    private void makeConnection()
    {
        hubProxy = conn.createHubProxy("bleed");
        conn.start().done(new Action<Void>() {
            @Override
            public void run(Void obj) throws Exception {
                sendShare();
            }
        }).onError(new ErrorCallback() {
            @Override
            public void onError(Throwable error) {
                Log.e("ninad",error.getMessage());
            }
        });
    }

    private void sendShare()
    {
        String action = mIntent.getAction();
        if (ACTION_TEXT.equals(action)) {
            final String param1 = mIntent.getStringExtra(QRCODE);
            final String param2 = mIntent.getStringExtra(EXTRA_PARAM);
            handleActionText(param1, param2);
        } else if (ACTION_IMAGE.equals(action)) {
            final String param1 = mIntent.getStringExtra(QRCODE);
            final String param2 = mIntent.getStringExtra(EXTRA_PARAM);
            handleActionImage(param1, param2);
        }
    }

    private void handleActionText(String param1, String param2) {

        Pattern pattern = Pattern.compile("/((([A-Za-z]{3,9}:(?:\\/\\/)?)(?:[-;:&=\\+\\$,\\w]+@)?[A-Za-z0-9.-]+|(?:www.|[-;:&=\\+\\$,\\w]+@)[A-Za-z0-9.-]+)((?:\\/[\\+~%\\" +
                "/._]*)?\\??(?:[-\\+=&;%@.\\w_]*)#?(?:[\\w]*))?)/");
        if(!pattern.matcher(param2).matches()) {
            hubProxy.invoke("Share", param1, "text/uri", param2);
        }
        else
        {
            hubProxy.invoke("Share", param1, "text/plain", param2);
        }
        wakeLock.release();
    }

    private void handleActionImage(final String param1, String param2) {
        final FileToUpload file = new FileToUpload(param2,"img1","Image");

        hubProxy.invoke(String.class,"GetFileKey",file.length()).done(new Action<String>() {
            @Override
            public void run(String obj) throws Exception {
                sendImage(obj,file,param1);
            }
        });

    }

    private void sendImage(final String fileKey,FileToUpload file, final String param1) {
        final long totalBytes = file.length();
        long uploadedBytes = 0;
        final int totalpages = (int) (totalBytes/CHUNK_SIZE) +1;


        InputStream stream = null;
        try {
            stream = file.getStream();
            byte[] buffer = new byte[CHUNK_SIZE];
            long bytesRead;
            final int[] page = {1,1};

            while ((bytesRead = stream.read(buffer, 0, buffer.length)) > 0) {
               // String stringToStore = new String(Base64.encode(buffer,Base64.DEFAULT));
                hubProxy.invoke(Boolean.class,"FileChunk",fileKey, page[0]++, buffer).done(new Action<Boolean>() {
                    @Override
                    public void run(Boolean obj) throws Exception {
                        Log.e("ninadlog","success invoke return "+obj);
                        if (page[1]++==totalpages)
                        {
                            completeImageShare(fileKey,param1);
                        }
                    }
                }).onError(new ErrorCallback() {
                    @Override
                    public void onError(Throwable error) {
                        Log.e("ninadlog","error invoke return "+error.getMessage());
                    }
                });
                uploadedBytes += bytesRead;
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            closeInputStream(stream);
            ;
        }

    }

    private void completeImageShare(String fileKey,String param1)
    {
        hubProxy.invoke("Share",param1,"image/jpeg",fileKey).onError(new ErrorCallback() {
            @Override
            public void onError(Throwable error) {
                Log.e("ninadlog","error share return "+error.getMessage());
            }
        });
        wakeLock.release();
    }

    private void closeInputStream(final InputStream stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (Exception exc) {
            }
        }
    }

}