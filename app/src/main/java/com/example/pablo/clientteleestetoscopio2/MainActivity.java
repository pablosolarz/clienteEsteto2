package com.example.pablo.clientteleestetoscopio2;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
public class MainActivity extends AppCompatActivity {
    private static final int MY_PERMISSIONS_REQUEST_READ_PHONE_STATE = 0;
    //Parámetros de audio
    private static final String TAG = "VoiceRecord";
    private static final int RECORDER_SAMPLERATE = 44100;
    private static final int RECORDER_CHANNELS_IN = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_CHANNELS_OUT = AudioFormat.CHANNEL_OUT_MONO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static final int AUDIO_SOURCE = MediaRecorder.AudioSource.MIC;
    // Initialize minimum buffer size in bytes.
    private int bufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE, RECORDER_CHANNELS_IN, RECORDER_AUDIO_ENCODING);
    private ArrayAdapter<String> listAdapter ;
    private AudioRecord recorder = null;
    private Thread recordingThread = null;
    private boolean isRecording = false;
    int fileCount;
    //Componentes de interfaz
    TextView txNroTel, txNroReceptores;
    Switch swConectar;
    private String salida, retorno;
    private Socket socketCliente = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        txNroTel = (TextView) findViewById(R.id.txNroTel);
        txNroReceptores = (TextView) findViewById(R.id.textNroReceptores);
        swConectar = (Switch) findViewById(R.id.switchConectar);

        final ConectaTCP conectaTCP = new ConectaTCP();

        idTelefono();

        iniNroReceptores();
        iniEstadoConexion();

        swConectar.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Log.v("Switch State=", "" + isChecked);
                conectaTCP.execute(txNroTel.getText().toString(), retorno,salida);
            }
        });
        Socket copiaSocket = socketCliente;
        //Activación audio rec
        setButtonHandlers();
        enableButtons(false);
        int bufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE, RECORDER_CHANNELS_IN, RECORDER_AUDIO_ENCODING);
        String pepe = "1";

    }
    private void setButtonHandlers() {
        ((Button) findViewById(R.id.btnStart)).setOnClickListener(btnClick);
        ((Button) findViewById(R.id.btnStop)).setOnClickListener(btnClick);
        ((Button) findViewById(R.id.btnPlay)).setOnClickListener(btnClick);
    }
    private View.OnClickListener btnClick = new View.OnClickListener() {
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.btnStart: {
                    enableButtons(true);
                    startRecording();
                    break;
                }
                case R.id.btnStop: {
                    enableButtons(false);
                    try {
                        stopRecording();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                }
                case R.id.btnPlay:{
                    //enableButtons(true);
                    try {
                        File folder = new File(Environment.getExternalStorageDirectory() +
                                File.separator + "Estetoscopio");
                        PlayShortAudioFileViaAudioTrack(folder + File.separator+"sndstet" + folder.listFiles().length + ".pcm");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }
        }
    };
    int BufferElements2Rec = 1024; // want to play 2048 (2K) since 2 bytes we use only 1024
    int BytesPerElement = 2; // 2 bytes in 16bit format
    private void startRecording() {
        if( bufferSize == AudioRecord.ERROR_BAD_VALUE)
            Log.e( TAG, "Bad Value for bufferSize recording parameters are not supported by the hardware");
        if( bufferSize == AudioRecord.ERROR )
            Log.e( TAG, "Bad Value for bufferSize implementation was unable to query the hardware for its output properties");
        Log.e( TAG, "bufferSize = "+ bufferSize);
        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                RECORDER_SAMPLERATE, RECORDER_CHANNELS_IN,
                RECORDER_AUDIO_ENCODING,BufferElements2Rec);
        //am.setSpeakerphoneOn(true);
        //Log.d("SPEAKERPHONE", "Is speakerphone on? : " + am.isSpeakerphoneOn());
        recorder.startRecording();
        //atrack.setPlaybackRate( RECORDER_SAMPLERATE);
        isRecording = true;
        Toast.makeText(MainActivity.this, "Inicio grabacion", Toast.LENGTH_LONG);
        recordingThread = new Thread(new Runnable() {
            public void run() {
                writeAudioDataToFile();
            }
        }, "AudioRecorder Thread");
        recordingThread.start();
        Toast.makeText(MainActivity.this, "Fin grabacion", Toast.LENGTH_LONG);
    }
    private void stopRecording() throws IOException {
        // stops the recording activity
        if (null != recorder) {
            isRecording = false;
            recorder.stop();
            recorder.release();
            recorder = null;
            recordingThread = null;
        }
    }
    private void PlayShortAudioFileViaAudioTrack(String filePath) throws IOException{
        // We keep temporarily filePath globally as we have only two sample sounds now..
        if (filePath==null)
            return;
        //Reading the file..
        File file = new File(filePath);
        byte[] byteData = new byte[(int) file.length()];
        Log.d(TAG, (int) file.length()+"");
        FileInputStream in = null;
        try {
            in = new FileInputStream( file );
            in.read( byteData );
            in.close();
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        // Set and push to audio track..
        int intSize = android.media.AudioTrack.getMinBufferSize(RECORDER_SAMPLERATE, RECORDER_CHANNELS_OUT, RECORDER_AUDIO_ENCODING);
        Log.d(TAG, intSize+"");
        AudioTrack at = new AudioTrack(AudioManager.STREAM_MUSIC, RECORDER_SAMPLERATE, RECORDER_CHANNELS_OUT, RECORDER_AUDIO_ENCODING, intSize, AudioTrack.MODE_STREAM);
        if (at!=null) {
            at.play();
            // Write the byte array to the track
            at.write(byteData, 0, byteData.length);
            at.stop();
            at.release();
        }
        else
            Log.d(TAG, "audio track is not initialised ");
    }
    private void writeAudioDataToFile() {
        boolean hecho;
        File folder = new File(Environment.getExternalStorageDirectory() +
                File.separator + "Estetoscopio");
        if (!folder.exists()) {
            if(folder.mkdirs()){
                Log.v(TAG,"Directorio creado");
            }else{
                Log.v(TAG,"Directorio NO creado");
            }
        }
        /*
        String pathEntero;
        pathEntero = folder.getAbsolutePath();
        // Write the output audio in byte
        fileCount = folder.listFiles().length + 1;
        //String filePath = "/sdcard/Sonidos Estetoscopio/ voice8K16bitmono"+fileCount+".pcm";
        String filePath = folder.getAbsolutePath() + fileCount + ".pcm";
        */
        int nroarchivo = folder.listFiles().length + 1;
        File fsonido = new File(folder,"sndstet" + nroarchivo + ".pcm");

        String spath = fsonido.getAbsolutePath();
        try {
            if(fsonido.createNewFile()){
                Log.v(TAG, "File created");
            }else {
                Log.v(TAG, "File doesn't created");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }



        short sData[] = new short[BufferElements2Rec];
        FileOutputStream os = null;
        OutputStream os2 = null;

        try {
            os = new FileOutputStream(fsonido);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        try {
            os2 = socketCliente.getOutputStream();

            PrintWriter ps2 = new PrintWriter(os2);
            ps2.println("Record");
            ps2.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        while (isRecording) {
            // gets the voice output from microphone to byte format
            //System.out.println("Short wirting to file" + sData.toString());
            byte bData[];// = short2byte(sData);
            try {
                recorder.read(sData, 0, BufferElements2Rec);
                bData = short2byte(sData);
              //  os.write(bData, 0, BufferElements2Rec * BytesPerElement);
                os2.write(bData, 0, BufferElements2Rec * BytesPerElement);
            } catch (IOException e) {
                e.printStackTrace();
            }
            //listenAudioDataToFile();
            //recorder.read((bData),0,BufferElements2Rec * BytesPerElement);
        }
        //Prueba al servidor
        try {
            os.close();
            os2.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private byte[] short2byte(short[] sData) {
        int shortArrsize = sData.length;
        byte[] bytes = new byte[shortArrsize * 2];
        for (int i = 0; i < shortArrsize; i++) {
            bytes[i * 2] = (byte) (sData[i] & 0x00FF);
            bytes[(i * 2) + 1] = (byte) (sData[i] >> 8);
            sData[i] = 0;
        }
        return bytes;
    }
    private void enableButton(int id, boolean isEnable) {
        ((Button) findViewById(id)).setEnabled(isEnable);
    }
    private void enableButtons(boolean isRecording) {
        enableButton(R.id.btnStart, !isRecording);
        enableButton(R.id.btnPlay, !isRecording);
        enableButton(R.id.btnStop, isRecording);
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_READ_PHONE_STATE: {
// If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
// permission was granted, yay! Do the
// contacts-related task you need to do.
                } else {
// permission denied, boo! Disable the
// functionality that depends on this permission.
                }
                return;
            }
// other 'case' lines to check for other
// permissions this app might request
        }
    }
    private void idTelefono() {
        /* TelephonyManager tMgr;
        tMgr = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        String idDispositivo = tMgr.getDeviceId();
        // if (idDispositivo != null) {
        if (!idDispositivo.isEmpty())
            txNroTel.setText(idDispositivo);
        else
            txNroTel.setText("ID Dispoitivo NO Encontrado");
            */

        txNroTel.setText("#12345");
    }
    private void iniNroReceptores() {
        txNroReceptores.setText("0");
    }
    private void iniEstadoConexion() {
        if (swConectar.isChecked())
            swConectar.toggle();
    }
    public class ConectaTCP extends AsyncTask<String, String, String> {
        private StringReader strr;
        private String frase, fraseModificada;
        private static final int SERVERPORT = 6789;
        private static final String SERVER_IP = "10.0.0.10";


        // private String str;
        public Socket conexion() {
            //Socket socketCliente = null;
            try {
                InetAddress serverAddr = InetAddress.getByName(SERVER_IP);
                socketCliente = new Socket(SERVER_IP, SERVERPORT);  //Aqui es detectada la conexión
            } catch (UnknownHostException e) {
                System.err.println("Trying to connect to unknown host: " + e);
            } catch (IOException e) {
                System.err.println("IOException:  " + e);
            } catch (Exception e) {
                System.err.println("Error:  " + e);
            }
            return socketCliente;
        }
        @Override
        protected String doInBackground(String... str) {
            strr = new StringReader(str[0]);
//String salida = new String();
            StringBuilder builder = new StringBuilder();
            //Socket socketCliente = null;
//DataOutputStream salidaAServidor;
            PrintStream salidaAServidor;
            String salida = "";
            String inputLine, responseLine, respuestaServidor, ultimaRespuesta = "";
            try {
                BufferedReader entradaDesdeUsuario = new BufferedReader(strr);
//InetAddress serverAddr = InetAddress.getByName(SERVER_IP);
//socketCliente = new Socket(SERVER_IP,SERVERPORT);  //Aqui es detectada la conexión
                socketCliente = this.conexion();
//txtView.setText(entradaDesdeUsuario.readLine());
//salidaAServidor = new DataOutputStream(socketCliente.getOutputStream());
                salidaAServidor = new PrintStream(socketCliente.getOutputStream());
                BufferedReader entradaDesdeServidor = new BufferedReader(new InputStreamReader(socketCliente.getInputStream()));

                if (socketCliente != null && salidaAServidor != null && entradaDesdeServidor != null) {
                    try {
/*
* Keep on reading from/to the socket till we receive the "Ok" from the
* server, once we received that then we break.
*/
//System.out.println("The client started. Type any text. To quit it type 'Ok'.");
                       // String tmpDesdeServidor;
                        //tmpDesdeServidor=entradaDesdeServidor.readLine();
                        inputLine = entradaDesdeUsuario.readLine();
                        responseLine = inputLine;
                        salidaAServidor.println(responseLine); //Aqui envía el texto de entrada al servidor
//responseLine = entradaDesdeServidor.readLine();
///////stream audio + publishProgress(.....)
                        respuestaServidor = entradaDesdeServidor.readLine();
                        if (!respuestaServidor.isEmpty() && !respuestaServidor.equals(ultimaRespuesta))  //Verificar si es el id de otro telefono
                            publishProgress(respuestaServidor);  //Incremento de usuarios activos (Aqui recibe la notificación de si mismo)
/*
* Close the output stream, close the input stream, close the socket.
*/
                        //salidaAServidor.close();
                        //entradaDesdeServidor.close();
                        //entradaDesdeUsuario.close();
                        //socketCliente.close();
                    } catch (UnknownHostException e) {
                        System.err.println("Trying to connect to unknown host: " + e);
                    } catch (IOException e) {
                        System.err.println("IOException:  " + e);
                    } catch (Exception e) {
                        System.err.println("Error:  " + e);
                    }
                }
            } catch (UnknownHostException e) {
                e.printStackTrace();
                System.out.println("Unknown host...");
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("Failed to connect...");
            } catch (Exception e) {
                e.printStackTrace();
                e.getMessage();
                System.out.println("Error...");
            }
            salida = "Chau";
            return salida;
        }
        @Override
        protected void onProgressUpdate(String... s) {
//     for (long i = 0; i < 1000000; i++) {
            txNroReceptores.setText(String.valueOf(Integer.valueOf(txNroReceptores.getText().toString()) + 1));
        }
        protected void onPostExecute(String s) {
            //Socket so = socketCliente;


            Toast t = Toast.makeText(MainActivity.this, s, Toast.LENGTH_LONG);
            t.show();
            //           iniEstadoConexion();
            //           iniNroReceptores();
        }
    }
    private void conexionStramming(){}
}
