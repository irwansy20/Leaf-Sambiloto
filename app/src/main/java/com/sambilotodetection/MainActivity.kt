package com.sambilotodetection

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.sambilotodetection.databinding.ActivityMainBinding
import com.sambilotodetection.databinding.PopupChooseImageSourceBinding
import com.sambilotodetection.ml.TransferModel
import com.sambilotodetection.utils.Utils
import com.sambilotodetection.utils.Utils.rotateBitmap
import com.sambilotodetection.utils.Utils.rotateFileImage
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    lateinit var bitmap : Bitmap
    private var progr = 0
    private var imageSize = 250
    private var currentFile: File? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()

        setupPopupMenu()

        //izin camera
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(
                this,
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun setupPopupMenu() {
        val popupBinding = PopupChooseImageSourceBinding.inflate(layoutInflater)
        val popupWindow = PopupWindow(
            popupBinding.root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            isFocusable = true
            elevation = 10F
            setOnDismissListener {
                // Saat popup ditutup, tampilkan kembali tombol "process"
                binding.process.visibility = View.VISIBLE
            }
        }

        popupBinding.btnCamera.setOnClickListener {
            startCamera()
            popupWindow.dismiss()
        }

        popupBinding.btnGallery.setOnClickListener {
            startGallery()
            popupWindow.dismiss()
        }

        binding.chooseButton.setOnClickListener { btn ->

            // Menyembunyikan tombol "process" saat popup muncul
            binding.process.visibility = View.GONE

            // Calculate the X and Y offsets for the popup window to be below the "Choose" button
            val xOffset = (binding.chooseButton.width - popupWindow.width) / 2
//            val yOffset = binding.chooseButton.height

            // Menampilkan popup di tengah dan di bawah tombol menggunakan showAsDropDown
            popupWindow.showAsDropDown(btn, 320, 0, Gravity.CENTER)
        }
    }

    private fun show(isLoad: Boolean) {
        binding.percent.visibility = if (isLoad) View.VISIBLE else View.GONE
        binding.vector3.visibility = if (isLoad) View.VISIBLE else View.GONE
        binding.progressPercentage.visibility = if (isLoad) View.VISIBLE else View.GONE
        binding.textViewResult.visibility = if (isLoad) View.VISIBLE else View.GONE
        binding.result.visibility = if (isLoad) View.VISIBLE else View.GONE
    }

    private fun updateProgressBar(){
        binding.progressPercentage.progress = progr
        binding.textViewResult.text = "$progr%"
    }

    //tensorflow lite
    private fun predictImage(image: Bitmap){
        val fileName = "label_leaf.txt"
        val app = application
        val inputString = app.assets.open(fileName).bufferedReader().use { it.readText() }
        val itemList = inputString.split("\n")

        val model = TransferModel.newInstance(this)

        val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 250, 250, 3), DataType.FLOAT32)
        val byteBuffer = ByteBuffer.allocateDirect(4 * imageSize * imageSize * 3)
        byteBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(imageSize * imageSize)
        image.getPixels(intValues, 0, image.width, 0, 0, image.width, image.height)

        var pixel = 0
        for (i in 0 until imageSize) {
            for (j in 0 until imageSize) {
                val `val` = intValues[pixel++] // RGB
                byteBuffer.putFloat((`val` shr 16 and 0xFF) * (1f / 255f))
                byteBuffer.putFloat((`val` shr 8 and 0xFF) * (1f / 255f))
                byteBuffer.putFloat((`val` and 0xFF) * (1f / 255f))
            }
        }
        inputFeature0.loadBuffer(byteBuffer)

        // Runs model inference and gets result.
        val outputs: TransferModel.Outputs = model.process(inputFeature0)
        val outputFeature0: TensorBuffer = outputs.outputFeature0AsTensorBuffer
        val confidences = outputFeature0.floatArray
        // find the index of the class with the biggest confidence.
        var maxPos = 0
        var maxConfidence = 0f
        for (i in confidences.indices) {
            if (confidences[i] > maxConfidence) {
                maxConfidence = confidences[i]
                maxPos = i
            }
        }


        val output = String.format("%s", itemList[maxPos])
        progr = (confidences[maxPos] * 100).toInt()
        updateProgressBar()
        binding.result.text = "Daun Sambiloto $output"

        model.close()
    }

    //camera
    private fun startCamera(){
        val camera = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        camera.resolveActivity(this.packageManager)
        val app = application

        Utils.createCustomTempFile(app).also {
            val photoURI: Uri = FileProvider.getUriForFile(
                this@MainActivity,
                "com.sambilotodetection",
                it
            )
            currentPhotoPath = it.absolutePath
            camera.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
            startActivityForResult(camera, 200)
        }
    }

    //gallery
    private fun startGallery() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"

        startActivityForResult(intent, 250)
    }

    //TODO Izin camera
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (!allPermissionsGranted()) {
                Toast.makeText(
                    this,
                    "Tidak mendapatkan permission.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    //fungsi modelling gambar
    lateinit var currentPhotoPath: String
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 250 && data !=null){
            binding.previewImageView.setImageURI(data.data)

            val uri: Uri ?=data.data
            val myFile = Utils.uriToFile(data.data as Uri, this)

            currentFile = (myFile)
            val resolver = this.contentResolver
            bitmap = MediaStore.Images.Media.getBitmap(resolver, uri)
            val image = Bitmap.createScaledBitmap(bitmap, imageSize, imageSize, true)
            binding.process.setOnClickListener {
                if (image != null){
                    predictImage(image)
                    show(true)
                }
            }
        }
        else if (requestCode == 200 && resultCode == Activity.RESULT_OK) {
            val myFile = File(currentPhotoPath)
            var result = rotateBitmap(BitmapFactory.decodeFile(myFile.path), true)

            val dimension = result.width.coerceAtMost(result.height)
            result = ThumbnailUtils.extractThumbnail(result, dimension, dimension)

            binding.previewImageView.setImageBitmap(result)

            currentFile = rotateFileImage(myFile)
            result = Bitmap.createScaledBitmap(result, imageSize, imageSize, true)
            binding.process.setOnClickListener {
                if (result != null) {
                    predictImage(result)
                    show(true)
                }
            }
        }
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val REQUEST_CODE_PERMISSIONS = 10
    }
}