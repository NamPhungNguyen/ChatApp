package com.example.chatapp.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.example.chatapp.databinding.ActivitySignUpBinding;
import com.example.chatapp.utilities.Constants;
import com.example.chatapp.utilities.PreferenceManager;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashMap;

public class SignUpActivity extends AppCompatActivity {
    private ActivitySignUpBinding binding;
    private PreferenceManager preferenceManager;
    private String encodedImage;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySignUpBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        preferenceManager = new PreferenceManager(getApplicationContext());
        clickListenners();
    }

    private void clickListenners(){
        binding.textSignIn.setOnClickListener(v->onBackPressed());
        binding.btnSignUp.setOnClickListener(v -> {
            if (isValidSignUpDetails()){
                signUp();
            }
        });
        binding.layoutImage.setOnClickListener(v->{
            //Một Intent mới được tạo với hành động là Intent.ACTION_PICK, điều này yêu cầu hệ thống mở một ứng dụng hoặc một phần tử của hệ thống để chọn một dữ liệu và trả về nó.
            //Uri MediaStore.Images.Media.EXTERNAL_CONTENT_URI được sử dụng để chỉ định rằng chúng ta muốn chọn một hình ảnh từ bộ nhớ ngoại vi của thiết bị.
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            //intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) được sử dụng để thêm cờ FLAG_GRANT_READ_URI_PERMISSION vào Intent.
            // Điều này là cần thiết khi chúng ta muốn chia sẻ quyền đọc (read permission) cho các ứng dụng khác để có thể đọc Uri của ảnh mà chúng ta chọn.
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            pickImage.launch(intent);
        });
    }



    private void signUp(){
        loading(true);
        //khoi tao firebasefirestore de thao tác với firestore
        FirebaseFirestore database = FirebaseFirestore.getInstance();

        //tạo hashmap chứa thông tin người dùng
        HashMap<String, Object> user = new HashMap<>();
        user.put(Constants.KEY_NAME, binding.inputNameSignUp.getText().toString());
        user.put(Constants.KEY_EMAIL, binding.inputEmailSignUp.getText().toString());
        user.put(Constants.KEY_PASSWORD, binding.inputPasswordSignUp.getText().toString());
        user.put(Constants.KEY_IMAGE, encodedImage);
        database.collection(Constants.KEY_COLLECTION_USERS)
                .add(user)
                .addOnSuccessListener(documentReference -> {
                    loading(false);
                    preferenceManager.putBoolean(Constants.KEY_IS_SIGNED_IN, true);
                    preferenceManager.putString(Constants.KEY_USER_ID, documentReference.getId());
                    preferenceManager.putString(Constants.KEY_NAME, binding.inputNameSignUp.getText().toString());
                    Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                    //các cờ (flags) được thêm vào để đảm bảo rằng khi màn hình mới được mở,
                    // các màn hình trước đó sẽ bị xóa khỏi ngăn xếp màn hình (stack),
                    // để người dùng không thể quay lại màn hình đăng ký.
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);

                })
                .addOnFailureListener(exception -> {
                    loading(false);
                    showToast(exception.getMessage());
                });
    }

    private String encodeImage(Bitmap bitmap){
        int previewWidth =150;
        int previewHeight = bitmap.getHeight() * previewWidth / bitmap.getWidth();
        Bitmap previewBitmap = Bitmap.createScaledBitmap(bitmap, previewWidth, previewHeight, false);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        previewBitmap.compress(Bitmap.CompressFormat.JPEG, 50, byteArrayOutputStream);
        byte[] bytes = byteArrayOutputStream.toByteArray();
        return Base64.encodeToString(bytes, Base64.DEFAULT);
    }

    private final ActivityResultLauncher<Intent> pickImage = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK){
                    if (result.getData() != null){ // neu thanh cong va khong rong, lay anh tu Uri của ảnh trả về
                        Uri imageUri = result.getData().getData();
                        try {
                            //Một InputStream được tạo ra từ Uri của ảnh, sau đó sử dụng BitmapFactory để giải mã InputStream thành một Bitmap (ảnh).
                            InputStream inputStream = getContentResolver().openInputStream(imageUri);
                            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                            //Bitmap này được hiển thị trong ImageView (binding.imageProfile), và TextView (binding.textAddImage) dùng để báo rằng ảnh đã được chọn sẽ được ẩn đi (setVisibility(View.GONE)).
                            binding.imageProfile.setImageBitmap(bitmap);
                            binding.textAddImage.setVisibility(View.GONE);
                            //ảnh được mã hóa thành một chuỗi base64 bằng cách sử dụng phương thức encodeImage(bitmap), và kết quả được lưu vào biến encodedImage.
                            encodedImage = encodeImage(bitmap);
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
    );

    // validate form sign up
    private Boolean isValidSignUpDetails() {
        if (encodedImage == null){
            showToast("Select profile image");
            return false;
        }else if (binding.inputNameSignUp.getText().toString().trim().isEmpty()){
            showToast("Enter name");
            return false;
        }else if (binding.inputEmailSignUp.getText().toString().trim().isEmpty()){
            showToast("Enter email");
            return false;
        }else if (!Patterns.EMAIL_ADDRESS.matcher(binding.inputEmailSignUp.getText().toString().trim()).matches()){
            showToast("Enter valid image");
            return false;
        }else if (binding.inputPasswordSignUp.getText().toString().trim().isEmpty()){
            showToast("Enter password");
            return false;
        }else if (binding.inputConfirmPassword.getText().toString().trim().isEmpty()){
            showToast("Enter confirm password");
            return false;
        }else if (!binding.inputPasswordSignUp.getText().toString().equals(binding.inputConfirmPassword.getText().toString())){
            showToast("Password and confirm password must be same");
            return false;
        }else {
            return true;
        }
    }


    // progressBar
    private void loading(Boolean isLoading){
        if (isLoading){
            binding.btnSignUp.setVisibility(View.VISIBLE);
            binding.progressBar.setVisibility(View.INVISIBLE);
        }else {
            binding.btnSignUp.setVisibility(View.INVISIBLE);
            binding.progressBar.setVisibility(View.VISIBLE);
        }
    }

    private void showToast(String message){
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }
}