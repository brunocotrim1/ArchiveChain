#include "sloth256_189.h"
#include "fcul_wrapper_Sloth.h"
#include <stddef.h>
#include <stdint.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <time.h>


JNIEXPORT jbyteArray JNICALL Java_fcul_wrapper_Sloth_encode
  (JNIEnv *env, jobject obj, jbyteArray inoutArray, jbyteArray ivArray, jint layers) {

    // Convert the Java byte array to C byte array
    jsize inoutLength = env->GetArrayLength(inoutArray);
    jbyte *inoutElements = env->GetByteArrayElements(inoutArray, NULL);
    
    jsize ivLength = env->GetArrayLength(ivArray);
    jbyte *ivElements = env->GetByteArrayElements(ivArray, NULL);

    // Debugging: Print the sizes of the arrays
    if (ivLength != 32) {
        // Error: IV must be of length 32
        printf("IV length is incorrect. Expected 32, got %d\n", ivLength);

        env->ReleaseByteArrayElements(inoutArray, inoutElements, 0);
        env->ReleaseByteArrayElements(ivArray, ivElements, 0);
        return NULL;
    }
    unsigned char inoutCArray[inoutLength];
    unsigned char ivCArray[ivLength];
  
    //Copy data from arrays to C arrays
    for (int i = 0; i < inoutLength; i++) {
        inoutCArray[i] = (unsigned char)inoutElements[i];
    }

    for (int i = 0; i < ivLength; i++) {
        ivCArray[i] = (unsigned char)ivElements[i];
    }

    int result = sloth256_189_encode(inoutCArray, inoutLength, ivCArray, layers);

    jbyteArray byteArray = env->NewByteArray(inoutLength);
    env->SetByteArrayRegion(byteArray, 0, inoutLength, (jbyte *)inoutCArray);
    env->ReleaseByteArrayElements(inoutArray, inoutElements, 0);
    env->ReleaseByteArrayElements(ivArray, ivElements, 0);
    return result == 0 ? byteArray : NULL;
}

JNIEXPORT jbyteArray JNICALL Java_fcul_wrapper_Sloth_decode
  (JNIEnv *env, jobject obj, jbyteArray inoutArray, jbyteArray ivArray, jint layers) {

    jsize inoutLength = env->GetArrayLength(inoutArray);
    jbyte *inoutElements = env->GetByteArrayElements(inoutArray, NULL);
    
    jsize ivLength = env->GetArrayLength(ivArray);
    jbyte *ivElements = env->GetByteArrayElements(ivArray, NULL);

    unsigned char inoutCArray[inoutLength];
    unsigned char ivCArray[ivLength];
    //Copy data from arrays to C arrays
    for (int i = 0; i < inoutLength; i++) {
        inoutCArray[i] = (unsigned char)inoutElements[i];
    }

    for (int i = 0; i < ivLength; i++) {
        ivCArray[i] = (unsigned char)ivElements[i];
    }
    sloth256_189_decode(inoutCArray, inoutLength, ivCArray, layers);
    jbyteArray byteArray = env->NewByteArray(inoutLength);
    env->SetByteArrayRegion(byteArray, 0, inoutLength, (jbyte *)inoutCArray);
    env->ReleaseByteArrayElements(inoutArray, inoutElements, 0);
    env->ReleaseByteArrayElements(ivArray, ivElements, 0);
    return byteArray;
}


// JNIEXPORT jboolean JNICALL Java_fcul_wrapper_Sloth_encode
//   (JNIEnv *env, jobject obj, jbyteArray inoutArray, jbyteArray ivArray, jint layers) {

//     // Convert the Java byte array to C byte array
//     jsize inoutLength = env->GetArrayLength(inoutArray);
//     jbyte *inoutElements = env->GetByteArrayElements(inoutArray, NULL);
    
//     jsize ivLength = env->GetArrayLength(ivArray);
//     jbyte *ivElements = env->GetByteArrayElements(ivArray, NULL);

//     // Debugging: Print the sizes of the arrays
//     printf("inoutLength: %d, ivLength: %d\n", inoutLength, ivLength);
//     if (ivLength != 32) {
//         // Error: IV must be of length 32
//         printf("IV length is incorrect. Expected 32, got %d\n", ivLength);

//         env->ReleaseByteArrayElements(inoutArray, inoutElements, 0);
//         env->ReleaseByteArrayElements(ivArray, ivElements, 0);
//         return JNI_FALSE;
//     }

//     // Debugging: Print the contents of the byte arrays (inoutArray and ivArray)
//     printf("inoutElements: ");
//     for (jsize i = 0; i < inoutLength; i++) {
//         printf("%02x", inoutElements[i]);
//     }
//     printf("\n");

//     printf("ivElements: ");
//     for (jsize i = 0; i < ivLength; i++) {  // Assuming ivElements length is 32
//         printf("%02x", ivElements[i]);
//     }
//     printf("\n");

//     printf("layers: %d\n", layers);  // Debugging: Print layers

//     // Call the encoding function
//     int result = sloth256_189_encode((unsigned char *)inoutElements, inoutLength, (const unsigned char *)ivElements, (size_t)layers);
//     printf("Encoding result: %d\n", result);  // Debugging: Print the result of encoding

//     // Release the byte arrays and commit changes to Java arrays if needed
//     env->ReleaseByteArrayElements(inoutArray, inoutElements, JNI_COMMIT); // Commit changes to Java byte array
//     env->ReleaseByteArrayElements(ivArray, ivElements, 0); // No changes to be written back to ivArray

//     return (result != 0) ? JNI_TRUE : JNI_FALSE;
// }

// JNIEXPORT void JNICALL Java_fcul_wrapper_Sloth_decode
//   (JNIEnv *env, jobject obj, jbyteArray inoutArray, jbyteArray ivArray, jint layers) {

//     // Convert the Java byte array to C byte array
//     jsize inoutLength = env->GetArrayLength(inoutArray);
//     jbyte *inoutElements = env->GetByteArrayElements(inoutArray, NULL);
    
//     jsize ivLength = env->GetArrayLength(ivArray);
//     jbyte *ivElements = env->GetByteArrayElements(ivArray, NULL);

//     // Debugging: Print the sizes of the arrays
//     printf("inoutLength: %d, ivLength: %d\n", inoutLength, ivLength);
//     if (ivLength != 32) {
//         // Error: IV must be of length 32
//         printf("IV length is incorrect. Expected 32, got %d\n", ivLength);

//         env->ReleaseByteArrayElements(inoutArray, inoutElements, 0);
//         env->ReleaseByteArrayElements(ivArray, ivElements, 0);
//         return; // No return type to handle errors, just exit.
//     }

//     // Debugging: Print the contents of the byte arrays (inoutArray and ivArray)
//     printf("inoutElements: ");
//     for (jsize i = 0; i < inoutLength; i++) {
//         printf("%02x", inoutElements[i]);
//     }
//     printf("\n");

//     printf("ivElements: ");
//     for (jsize i = 0; i < ivLength; i++) {  // Assuming ivElements length is 32
//         printf("%02x", ivElements[i]);
//     }
//     printf("\n");

//     printf("layers: %d\n", layers);  // Debugging: Print layers

//     // Call the decoding function
//     sloth256_189_decode((unsigned char *)inoutElements, inoutLength, (const unsigned char *)ivElements, (size_t)layers);
//     printf("Decoding finished!\n");

//     // Release the byte arrays and commit changes to Java arrays if needed
//     env->ReleaseByteArrayElements(inoutArray, inoutElements, JNI_COMMIT); // Commit changes to inoutArray
//     env->ReleaseByteArrayElements(ivArray, ivElements, 0); // No changes to be written back to ivArray
// }



// Helper function to generate random bytes
void generate_random_bytes(unsigned char *buffer, size_t size) {
    for (size_t i = 0; i < size; i++) {
        buffer[i] = rand() % 256;  // Generate a random byte (0 to 255)
    }
}

// Function to compare two byte arrays
int compare_byte_arrays(const unsigned char *arr1, const unsigned char *arr2, size_t size) {
    for (size_t i = 0; i < size; i++) {
        if (arr1[i] != arr2[i]) {
            return 0;  // Arrays are not equal
        }
    }
    return 1;  // Arrays are equal
}

// Simulate the Java byte array as C arrays
void test_encoding_decoding(unsigned char *piece, size_t piece_len, unsigned char *ivArray, size_t iv_len, size_t layers) {
    // Print debug info
    printf("IV length: %zu\n", iv_len);
    if (iv_len != 32) {
        // Error: IV must be of length 32
        printf("Error: IV length different from 32\n");
        return;
    }

    printf("piece: ");
    for (size_t i = 0; i < piece_len; i++) {
        printf("%02x ", piece[i]); // Print each byte in hex format
    }
    printf("\n");

    printf("piece_len: %zu\n", piece_len);

    printf("layers: %zu\n", layers);

    // Encoding step
    unsigned char encoding[piece_len];
    memcpy(encoding, piece, piece_len);  // Copy the piece to encoding array
    int encode_result = sloth256_189_encode(encoding, piece_len, ivArray, layers);
    if (encode_result != 0) {
        printf("Encoding failed! Result: %d\n", encode_result);
        return;
    }
    printf("Encoding finished!\n");

    // Decoding step
    unsigned char decoding[piece_len];
    memcpy(decoding, encoding, piece_len);  // Copy encoded data to decoding array
    sloth256_189_decode(decoding, piece_len, ivArray, layers);
    printf("Decoding finished!\n");

    // Assertion step: Compare original piece with decoded data
    if (compare_byte_arrays(piece, decoding, piece_len)) {
        printf("Success: The original and decoded pieces match!\n");
    } else {
        printf("Error: The original and decoded pieces do not match!\n");
    }
}

int main() {
    srand(time(NULL));  // Seed the random number generator

    // Generate random bytes for expanded IV (32 bytes)
    unsigned char expanded_iv[32];
    generate_random_bytes(expanded_iv, 32);

    // Generate random bytes for piece (4096 bytes)
    unsigned char piece[2048];
    generate_random_bytes(piece, 2048);

    // Calculate layers
    size_t layers = 2048 / 32;

    // Call the test function for encoding and decoding
    test_encoding_decoding(piece, 2048, expanded_iv, 32, layers);

    return 0;
}

//COMPILE INTO SHARED LIBRARY
//g++ -std=c++17 -shared -o sloth.so -fPIC -I${JAVA_HOME}/include -I${JAVA_HOME}/include/darwin sloth_wrapper.cpp sloth256_189.o -I/opt/homebrew/opt/openssl@3/include/ -L/opt/homebrew/opt/openssl@3/lib/ -lcrypto
//g++ -std=c++17 -shared -o sloth.so -fPIC -I${JAVA_HOME}/include -I${JAVA_HOME}/include/darwin sloth_wrapper.cpp sloth256_189.o -I/opt/homebrew/opt/openssl@3/include/ -L/opt/homebrew/opt/openssl@3/lib/ -lcrypto

//COMPILE MAIN
//g++ -std=c++17 -o sloth_test sloth_wrapper.cpp -fPIC -I${JAVA_HOME}/include -I${JAVA_HOME}/include/darwin sloth256_189.o -I/opt/homebrew/opt/openssl@3/include/ -L/opt/homebrew/opt/openssl@3/lib/ -lcrypto