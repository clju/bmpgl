package com.example.cjulliard.bmpgl;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Created by cjulliard on 7/30/15.
 */
public class MyRenderer implements GLSurfaceView.Renderer {

    private FloatBuffer verticesCoordBuffer;
    private FloatBuffer texCoordBuffer;
    private ByteBuffer textureBuffer;
    private int textureDataHandle;

    private int program;
    private final static String TAG = "MyRenderer";
    private int imgWidth;
    private int imgHeight;

    public MyRenderer(Context context, Uri fileUri) {
        try {
            String[] projection = { MediaStore.Images.Media.DATA };
            Cursor cursor = context.getContentResolver().query(fileUri, projection, null, null, null);
            cursor.moveToFirst();
            String path =cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA));
            cursor.close();

            FileInputStream fileInputStream = new FileInputStream(new File(path));
            byte[] buffer = new byte[4];
            fileInputStream.skip(10);
            fileInputStream.read(buffer);
            int offset = (buffer[0]&0xff) | (buffer[1]&0xff)<<8 | (buffer[2]&0xff)<<16 | (buffer[3]&0xff)<<24;
            fileInputStream.skip(4);
            fileInputStream.read(buffer);
            imgWidth = (buffer[0]&0xff) | (buffer[1]&0xff)<<8 | (buffer[2]&0xff)<<16 | (buffer[3]&0xff)<<24;
            fileInputStream.read(buffer);
            imgHeight = (buffer[0]&0xff) | (buffer[1]&0xff)<<8 | (buffer[2]&0xff)<<16 | (buffer[3]&0xff)<<24;
            fileInputStream.skip(offset - 26);

            int rowSize = 4+4*((3*imgWidth-1)/4);
            byte[] lineBuffer = new byte[rowSize];
            byte[] texture = new byte[imgWidth*imgHeight*3];
            for(int i=0;i<imgHeight;i++) {
                fileInputStream.read(lineBuffer);
                System.arraycopy(lineBuffer, 0, texture, i*imgWidth*3, imgWidth*3);
            }

            textureBuffer = ByteBuffer.allocateDirect(texture.length).order(ByteOrder.nativeOrder());
            textureBuffer.put(texture).position(0);

        } catch (IOException e) {
            Log.e(TAG, e.toString());
        }

        float textCoord[] = {
                1f, 0f,    1f, 1f,   0f, 1f,
                1f, 0f,    0f, 1f,   0f, 0f
        };

        texCoordBuffer = ByteBuffer.allocateDirect(textCoord.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        texCoordBuffer.put(textCoord).position(0);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {

        String stringVertexShader =
                "#version 100\n" +
                        "attribute vec2 a_position;" +
                        "attribute vec2 a_texCoord;" +
                        "varying vec2 v_texCoord;" +
                        "void main()" +
                        "{" +
                        "v_texCoord = a_texCoord;" +
                        "gl_Position = vec4(a_position.xy,0.0,1.0);" +
                        "}";
        String stringFragmentShader =
                "#version 100\n" +
                        "uniform sampler2D u_image;" +
                        "varying vec2 v_texCoord;" +
                        "void main()" +
                        "{" +
                        "gl_FragColor = texture2D(u_image,v_texCoord).bgra;" +
                        "}";


        int vertexShader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
        GLES20.glShaderSource(vertexShader, stringVertexShader);
        GLES20.glCompileShader(vertexShader);

        int fragmentShader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        GLES20.glShaderSource(fragmentShader, stringFragmentShader);
        GLES20.glCompileShader(fragmentShader);

        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);

        GLES20.glLinkProgram(program);

        int[] hTex = new int[1];
        GLES20.glGenTextures(1, hTex, 0);
        textureDataHandle = hTex[0];

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureDataHandle);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGB, imgWidth, imgHeight, 0, GLES20.GL_RGB, GLES20.GL_UNSIGNED_BYTE, textureBuffer);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);

    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {

        double ratio = Math.min(((double) width) / imgWidth, ((double) height) / imgHeight);
        float x0 = (float) (2*(((width-imgWidth*ratio)/2)/width)-1);
        float y0 = (float) (2*(((height-imgHeight*ratio)/2)/height)-1);

        float vertexCoords[] = {
                -x0, y0,    -x0,-y0,   x0,-y0,
                -x0, y0,     x0,-y0,   x0, y0
        };

        verticesCoordBuffer = ByteBuffer.allocateDirect(vertexCoords.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        verticesCoordBuffer.put(vertexCoords).position(0);

    }

    @Override
    public void onDrawFrame(GL10 gl) {

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glUseProgram(program);

        int vertHandle = GLES20.glGetAttribLocation(program, "a_position");
        GLES20.glEnableVertexAttribArray(vertHandle);
        verticesCoordBuffer.position(0);
        GLES20.glVertexAttribPointer(vertHandle, 2, GLES20.GL_FLOAT, false, 0, verticesCoordBuffer);

        int texCoordHandle = GLES20.glGetAttribLocation(program, "a_texCoord");
        GLES20.glEnableVertexAttribArray(texCoordHandle);
        texCoordBuffer.position(0);
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer);

        int textureUniformHandle = GLES20.glGetUniformLocation(program, "u_image");
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureDataHandle);
        GLES20.glUniform1i(textureUniformHandle, 0);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);

        GLES20.glDisableVertexAttribArray(vertHandle);
        GLES20.glDisableVertexAttribArray(texCoordHandle);
    }
}