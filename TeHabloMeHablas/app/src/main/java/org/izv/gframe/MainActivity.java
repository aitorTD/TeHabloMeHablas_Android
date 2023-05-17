package org.izv.gframe;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.dialogflow.v2.DetectIntentRequest;
import com.google.cloud.dialogflow.v2.DetectIntentResponse;
import com.google.cloud.dialogflow.v2.QueryInput;
import com.google.cloud.dialogflow.v2.SessionName;
import com.google.cloud.dialogflow.v2.SessionsClient;
import com.google.cloud.dialogflow.v2.SessionsSettings;
import com.google.cloud.dialogflow.v2.TextInput;
import com.google.common.collect.Lists;

import java.io.InputStream;
import java.util.UUID;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.text.ParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static android.speech.tts.TextToSpeech.SUCCESS;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.app.Activity;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    Button btSendToDialogFlow;
    EditText etSendToDialogFlow;
    TextView tvResponseFromDialogFlow;

    SessionsClient sessionClient;
    SessionName sessionName;

    Button btSpeechToText;
    ActivityResultLauncher<Intent> sttLauncher;
    Intent sttIntent;
    TextToSpeech tts;

    boolean ttsReady = false;
    final static String UNIQUE_UUID = UUID.randomUUID().toString();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();
        sttLauncher = getSttLauncher();
        sttIntent = getSttIntent();
        tts = new TextToSpeech(this, status -> {
            if(status == SUCCESS) {
                ttsReady = true;
                tts.setLanguage(new Locale("spa", "ES"));
            }
        });

        btSpeechToText = findViewById(R.id.btSpeechToText);
        btSpeechToText.setOnClickListener(v -> {
            initSpeechToText();
        });
    }

    private void init() {
        btSendToDialogFlow = findViewById(R.id.btSendToDialogFlow);
        etSendToDialogFlow = findViewById(R.id.etSendToDialogFlow);
        tvResponseFromDialogFlow = findViewById(R.id.tvResponseFromDialogFlow);

        if (setupDialogflowClient()) {
            btSendToDialogFlow.setOnClickListener(v -> {
                sendToDialogFlow();
            });
        } else {
            btSendToDialogFlow.setEnabled(false);
        }

    }

    private void sendToDialogFlow() {
        String text = etSendToDialogFlow.getText().toString();
        etSendToDialogFlow.setText("");
        if(!text.isEmpty()) {
            sendMessageToBot(text);
        } else {
            Toast.makeText(this, "Si escribes algo mejor, crack.", Toast.LENGTH_SHORT).show();
        }
    }

    private ActivityResultLauncher<Intent> getSttLauncher() {
        return registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    String text = "Ups";
                    if(result.getResultCode() == Activity.RESULT_OK) {
                        List<String> r = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                        text = r.get(0);
                    } else if(result.getResultCode() == Activity.RESULT_CANCELED) {
                        text = "Error";
                    }
                    showAndTalkResult(text);
                }
        );
    }

    private Intent getSttIntent() {
        Intent intencionSpeechToText = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intencionSpeechToText.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intencionSpeechToText.putExtra(RecognizerIntent.EXTRA_LANGUAGE, new Locale("spa", "ES"));
        intencionSpeechToText.putExtra(RecognizerIntent.EXTRA_PROMPT, "Por favor, hable ahora.");
        return intencionSpeechToText;
    }

    private void initSpeechToText() {
        sttLauncher.launch(sttIntent);
    }

    private void showAndTalkResult(String result) {
        sendMessageToBot(result); // Aquí estamos enviando el texto reconocido a DialogFlow.
    }


    private boolean setupDialogflowClient() {
        boolean value = true;
        try {
            InputStream stream = this.getResources().openRawResource(R.raw.client);
            GoogleCredentials credentials = GoogleCredentials.fromStream(stream)
                    .createScoped(Lists.newArrayList("https://www.googleapis.com/auth/cloud-platform"));
            String projectId = ((ServiceAccountCredentials) credentials).getProjectId();
            SessionsSettings.Builder settingsBuilder = SessionsSettings.newBuilder();
            SessionsSettings sessionsSettings = settingsBuilder.setCredentialsProvider(
                    FixedCredentialsProvider.create(credentials)).build();
            sessionClient = SessionsClient.create(sessionsSettings);
            sessionName = SessionName.of(projectId, UNIQUE_UUID);
        } catch (Exception e) {
            showMessage("\nexception in setupBot: " + e.getMessage() + "\n");
            value = false;
        }
        return value;
    }

    private void showMessage(String message) {
        runOnUiThread(() -> {
            tvResponseFromDialogFlow.append(message + "\n");
        });
    }

    private void sendMessageToBot(String message) {
        QueryInput input = QueryInput.newBuilder().setText(TextInput.newBuilder().setText(message).setLanguageCode("es-ES")).build();
        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    DetectIntentRequest detectIntentRequest =
                            DetectIntentRequest.newBuilder()
                                    .setSession(sessionName.toString())
                                    .setQueryInput(input)
                                    .build();
                    DetectIntentResponse detectIntentResponse = sessionClient.detectIntent(detectIntentRequest);
                    if(detectIntentResponse != null) {
                        //intent, action, sentiment
                        String action = detectIntentResponse.getQueryResult().getAction();
                        String intent = detectIntentResponse.getQueryResult().getIntent().toString();
                        String sentiment = detectIntentResponse.getQueryResult().getSentimentAnalysisResult().toString();
                        String botReply = detectIntentResponse.getQueryResult().getFulfillmentText();
                        if(!botReply.isEmpty()) {
                            // Regex para extraer la fecha ISO 8601
                            Pattern pattern = Pattern.compile("(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\+\\d{2}:\\d{2})");
                            Matcher matcher = pattern.matcher(botReply);

                            // Si se encuentra una fecha en la respuesta
                            if (matcher.find()) {
                                String isoDateStr = matcher.group(1); // fecha en formato ISO 8601
                                SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
                                SimpleDateFormat desiredFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm");

                                try {
                                    // Convertir la fecha ISO 8601 a un formato más legible
                                    Date date = isoFormat.parse(isoDateStr);
                                    String desiredDateStr = desiredFormat.format(date);

                                    // Reemplazar la fecha ISO 8601 en la respuesta original con la nueva fecha formateada
                                    botReply = botReply.replace(isoDateStr, desiredDateStr);
                                } catch (ParseException e) {
                                    e.printStackTrace();
                                }
                            }

                            // Nueva regex para extraer la fecha en formato "dd-MM-yyyy HH:mm"
                            Pattern pattern2 = Pattern.compile("(\\d{2})-(\\d{2})-(\\d{4}) (\\d{2}:\\d{2})");
                            Matcher matcher2 = pattern2.matcher(botReply);

                            // Si se encuentra una fecha en la respuesta
                            if (matcher2.find()) {
                                String dia = matcher2.group(1); // Día
                                String mes = matcher2.group(2); // Mes
                                String año = matcher2.group(3); // Año
                                String hora = matcher2.group(4); // Hora

                                // Reconstruir la fecha en el formato deseado
                                String fechaFormateada = dia + " del " + mes + " de " + año + " a las " + hora;

                                // Reemplazar la fecha original en la respuesta con la nueva fecha formateada
                                botReply = botReply.replace(matcher2.group(0), fechaFormateada);
                            }

                            if(ttsReady) {
                                tts.speak(botReply, TextToSpeech.QUEUE_ADD, null, null);
                            }
                            showMessage(botReply + "\n");
                        } else {
                            showMessage("something went wrong\n");
                        }
                    } else {
                        showMessage("connection failed\n");
                    }
                } catch (Exception e) {
                    showMessage("\nexception in thread: " + e.getMessage() + "\n");
                    e.printStackTrace();
                }
            }
        };
        thread.start();
    }
}