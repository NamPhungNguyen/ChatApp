package com.example.chatapp.activities;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;

import androidx.annotation.NonNull;

import com.example.chatapp.adapters.ChatAdapter;
import com.example.chatapp.databinding.ActivityChatBinding;
import com.example.chatapp.models.ChatMessage;
import com.example.chatapp.models.User;
import com.example.chatapp.utilities.Constants;
import com.example.chatapp.utilities.PreferenceManager;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class ChatActivity extends BaseActivity {
    private ActivityChatBinding binding;
    private User receivedUser;
    private List<ChatMessage> chatMessages;
    private ChatAdapter chatAdapter;
    private PreferenceManager preferenceManager;
    private FirebaseFirestore databse;
    private String conversionId = null;
    private Boolean isReceiverAvailable = false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setListeners();
        loadReceiverDetails();
        init();
        listenMessage();
    }

    private void setListeners(){
        binding.imageBack.setOnClickListener(v->onBackPressed());
        binding.layoutSend.setOnClickListener(v->sendMessage());
    }

    private void init(){
        preferenceManager = new PreferenceManager(getApplicationContext());
        chatMessages = new ArrayList<>();
        chatAdapter = new ChatAdapter(
                chatMessages,
                getBitmapFromEncodedString(receivedUser.image),
                preferenceManager.getString(Constants.KEY_USER_ID)
        );
        binding.chatRecyclerView.setAdapter(chatAdapter);
        databse = FirebaseFirestore.getInstance();
    }
    private void sendMessage(){
        //tạo một bản ghi mới trong firebase chứa thông tin về tin nhắn
        HashMap<String, Object> message = new HashMap<>();
        message.put(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
        message.put(Constants.KEY_RECEIVER_ID, receivedUser.id);
        message.put(Constants.KEY_MESSAGE, binding.inputMessage.getText().toString());
        message.put(Constants.KEY_TIMESTAMP, new Date());
        databse.collection(Constants.KEY_COLLECTION_CHAT).add(message);
        if (conversionId != null){
            updateConversion(binding.inputMessage.getText().toString());
        }else {
            HashMap<String, Object> conversion = new HashMap<>();
            conversion.put(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
            conversion.put(Constants.KEY_SENDER_NAME, preferenceManager.getString(Constants.KEY_NAME));
            conversion.put(Constants.KEY_SENDER_IMAGE, preferenceManager.getString(Constants.KEY_IMAGE));
            conversion.put(Constants.KEY_RECEIVER_ID, receivedUser.id);
            conversion.put(Constants.KEY_RECEIVER_NAME, receivedUser.name);
            conversion.put(Constants.KEY_RECEIVER_IMAGE, receivedUser.image);
            conversion.put(Constants.KEY_LAST_MESSAGE, binding.inputMessage.getText().toString());
            conversion.put(Constants.KEY_TIMESTAMP, new Date());
            addConversion(conversion);
        }
        binding.inputMessage.setText(null);
    }

    private void listenAvailabilityOfReceiver(){
        databse.collection(Constants.KEY_COLLECTION_USERS).document(
                receivedUser.id
        ).addSnapshotListener(ChatActivity.this, (value, error) -> {
            if (error != null){
                return;
            }
            if (value != null){
                if (value.getLong(Constants.KEY_AVAILABILITY) != null){
                    int availability = Objects.requireNonNull(
                            value.getLong(Constants.KEY_AVAILABILITY)
                    ).intValue();
                    isReceiverAvailable = availability == 1;
                }
            }
            if (isReceiverAvailable){
                binding.textAvailability.setVisibility(View.VISIBLE);
            }else {
                binding.textAvailability.setVisibility(View.GONE);
            }
        });
    }

    private void listenMessage(){
        // lắng nghe sự kiện để nhận tin nhắn từ firebase và cập nhật giao diện
        databse.collection(Constants.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .whereEqualTo(Constants.KEY_RECEIVER_ID, receivedUser.id)
                .addSnapshotListener(eventListener);
        databse.collection(Constants.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constants.KEY_SENDER_ID, receivedUser.id)
                .whereEqualTo(Constants.KEY_RECEIVER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .addSnapshotListener(eventListener);
    }

    private final EventListener<QuerySnapshot> eventListener = (value, error) -> {
      if (error != null){
          return;
      }
      if (value != null){
          int count = chatMessages.size();
          for (DocumentChange documentChange : value.getDocumentChanges()){
              if (documentChange.getType() == DocumentChange.Type.ADDED){
                  ChatMessage chatMessage = new ChatMessage();
                  chatMessage.senderId = documentChange.getDocument().getString(Constants.KEY_SENDER_ID);
                  chatMessage.receiverId = documentChange.getDocument().getString(Constants.KEY_RECEIVER_ID);
                  chatMessage.message = documentChange.getDocument().getString(Constants.KEY_MESSAGE);
                  chatMessage.dateTime = getReableDateTime(documentChange.getDocument().getDate(Constants.KEY_TIMESTAMP));
                  chatMessage.dateObject = documentChange.getDocument().getDate(Constants.KEY_TIMESTAMP);
                  chatMessages.add(chatMessage);
              }
          }
          Collections.sort(chatMessages, (obj1, obj2) -> obj1.dateObject.compareTo(obj2.dateObject));
          if (count == 0){
              chatAdapter.notifyDataSetChanged();
          }else {
              chatAdapter.notifyItemRangeInserted(chatMessages.size(), chatMessages.size());
              binding.chatRecyclerView.smoothScrollToPosition(chatMessages.size() - 1);
          }
          binding.chatRecyclerView.setVisibility(View.VISIBLE);
      }
      binding.progressBar.setVisibility(View.GONE);
      if (conversionId == null){
          checkForConversion();
      }
    };
    private Bitmap getBitmapFromEncodedString(String encodedImage){
        byte[] bytes = Base64.decode(encodedImage, Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }
    private void loadReceiverDetails(){
        //lấy thông tin về nguoi nhan tu intent và show name ra giao diện
        receivedUser = (User) getIntent().getSerializableExtra(Constants.KEY_USER);
        binding.textName.setText(receivedUser.name);
    }
    private String getReableDateTime(Date date){
        return new SimpleDateFormat("MMMM dd, yyyy - hh:mm a", Locale.getDefault()).format(date);
    }

    private void addConversion(HashMap<String, Object> conversion){
        databse.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .add(conversion)
                .addOnSuccessListener(documentReference -> conversionId = documentReference.getId());
    }


    private void updateConversion(String message){
        DocumentReference documentReference =
                databse.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                        .document(conversionId);
        documentReference.update(
                Constants.KEY_LAST_MESSAGE, message,
                Constants.KEY_TIMESTAMP, new Date()
        );
    }

    private void checkForConversion(){
        if (chatMessages.size() != 0){
            checkForConversionRemotely(
                    preferenceManager.getString(Constants.KEY_USER_ID),
                    receivedUser.id
            );
            checkForConversionRemotely(
                    receivedUser.id,
                    preferenceManager.getString(Constants.KEY_USER_ID)
            );
        }
    }

    private void checkForConversionRemotely(String senderId, String receiverId){
        databse.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .whereEqualTo(Constants.KEY_SENDER_ID, senderId)
                .whereEqualTo(Constants.KEY_RECEIVER_ID, receiverId)
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful() && task.getResult() != null && task.getResult().getDocuments().size() > 0){
                            DocumentSnapshot documentSnapsshot = task.getResult().getDocuments().get(0);
                            conversionId = documentSnapsshot.getId();
                        }
                    }
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        listenAvailabilityOfReceiver();
    }
}