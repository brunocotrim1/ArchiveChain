#include "por.hpp"
#include <iostream>
#include <chrono>
#include <iostream>
#include <filesystem>
#include <fstream> // Include the necessary header for file operations
#include <iostream>
#include <filesystem>
#include <vector>
#include <algorithm>
#include <jni.h>
#include <string>
// bool verify(unsigned char proof_a[], unsigned char challenge_a[]) {
//     std::string proof(proof_a);
//     std::string challenge(challenge_a);
//     por::PoRepT<32, merkle::sha256, 2, 64> por;
//     auto c = merkle::HashT<32>(challenge);
//     auto p = por::ProofT<32, 2, 64>(proof);
//     bool v = por.verify(p, c);

//     return v;
// }
namespace fs = std::filesystem;
bool verify(std::string proof, std::string challenge)
{
  por::PoRepT<32, merkle::sha256, 2, 64> por;
  auto c = merkle::HashT<32>(challenge);
  auto p = por::ProofT<32, 2, 64>(proof);
  bool v = por.verify(p, c);

  return v;
}
namespace fs = std::filesystem;

// Function to sort files based on their creation or modification time
std::vector<std::string> sortFilesByTime(const std::string &folderPath)
{
  std::vector<std::string> fileNames;

  // Iterate through the directory
  for (const auto &entry : fs::directory_iterator(folderPath))
  {
    // Check if the entry is a regular file
    if (fs::is_regular_file(entry.path()))
    {
      fileNames.push_back(entry.path().filename().string());
    }
  }

  // Sort files based on their last modification time
  std::sort(fileNames.begin(), fileNames.end(), [&](const std::string &a, const std::string &b)
            { return fs::last_write_time(fs::path(folderPath) / a) < fs::last_write_time(fs::path(folderPath) / b); });

  return fileNames;
}

void retrieveOriginal(std::string folderPath, por::PoRepT<32, merkle::sha256, 2, 64> por)
{
    std::vector<std::string> sortedFiles = sortFilesByTime(folderPath);

    // Open a file for writing
    std::ofstream outputFile("output"); // Change the filename as desired

    // Check if the file is opened successfully
    if (!outputFile.is_open()) {
        std::cerr << "Error: Unable to open output file." << std::endl;
        return;
    }

    // Print the sorted files
    for (const auto &file : sortedFiles)
    {
        if (file == "hello" || file == "hello1")
        {
            continue;
        }
        std::string file_path = folderPath + "/" + file;
        std::vector<std::vector<uint8_t>> fileBytes = por.retrieveOriginal(file_path);
        for (size_t i = 0; i < fileBytes.size(); ++i)
        {
            std::string stringValue(fileBytes[i].begin(), fileBytes[i].end());
            outputFile << stringValue; // Write each string value to the file
            //std::cout << stringValue << std::endl;
            //outputFile.write(reinterpret_cast<const char*>(fileBytes[i].data()), fileBytes[i].size());
        }
    }

    // Close the file
    outputFile.close();
}


extern "C" JNIEXPORT jboolean JNICALL
Java_fcul_blockchain_Test_nativeMethod(JNIEnv *env, jobject obj, jstring str1, jstring str2) {
    const char *nativeString1 = env->GetStringUTFChars(str1, nullptr);
    const char *nativeString2 = env->GetStringUTFChars(str2, nullptr);
    std::string challenge(nativeString1);
    std::string proof(nativeString2);
    // Your C++ logic here
    std::cout << "String 1: " << challenge << std::endl;
    std::cout << "String 2: " << proof << std::endl;
    bool v = verify(proof, challenge);
    // Release the allocated memory
    env->ReleaseStringUTFChars(str1, nativeString1);
    env->ReleaseStringUTFChars(str2, nativeString2);

    // Convert the C++ string to Java string and return
    return (jboolean)v;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_Test_nativeMethod(JNIEnv *env, jobject obj, jstring str1, jstring str2) {
    const char *nativeString1 = env->GetStringUTFChars(str1, nullptr);
    const char *nativeString2 = env->GetStringUTFChars(str2, nullptr);
    std::string challenge(nativeString1);
    std::string proof(nativeString2);
    // Your C++ logic here
    std::cout << "String 1: " << challenge << std::endl;
    std::cout << "String 2: " << proof << std::endl;
    bool v = verify(proof, challenge);
    // Release the allocated memory
    env->ReleaseStringUTFChars(str1, nativeString1);
    env->ReleaseStringUTFChars(str2, nativeString2);

    // Convert the C++ string to Java string and return
    return (jboolean)v;
}

int main(int argc, char **argv)
{
  if (argc < 1)
  {
    std::cout << "Please enter a file name to plot.\n";
    return 0;
  }
  por::PoRepT<32, merkle::sha256, 2, 64> p;

  std::chrono::steady_clock::time_point begin = std::chrono::steady_clock::now();
  p.plot(argv[1], "./plott/");
  std::chrono::steady_clock::time_point end = std::chrono::steady_clock::now();

  std::cout << "Conflicts: " << p.get_conflicts() << std::endl;
  std::cout << "Plots: " << p.get_plots() << std::endl;
  std::cout << "Execution time = " << std::chrono::duration_cast<std::chrono::milliseconds>(end - begin).count() << "[ms]" << std::endl;
  auto c = p.challenge();
  auto proof = p.generate_proof(c,"./plott/");
  // bool v = p.verify(proof, c);

  std::string ch = c.to_string();

  std::string pr = proof.to_string();

  // std::string ch("0000000000000000000000000000000000000000000000000000000000000000");
  // std::string pr("3b9d89bc8ae0c8b0c28ea6616151ce07c140f4ac7286b379736c7935d857a80d7c9189f2b1dad9f1eebcb66e2e3ce425fd6fde9e52f4dc4348464f04a65aa95518f4efd2ee85ae909dd1e931241f8d4b9e03abfa37d4e0303c22266ad274c16b643adbb06a68d1dd3d399b20853ceca318136837a7aa92d1da81c52d4b0342b9a9b1aad77595d9bff2491464c9c4212d6967cafb3eb58029376d703721aaf31b94c3419f8f11288bb312095346a5a5ea9b6e65545f20d314f4c1ca68f4440f7d5c4cc26de15f156b217fc94d292f3a24692ae36f654555fc09ee6e7270b9153e0e0c8761f9891a28d4673988434d3ceaaded9002e89aad2267f5f7b879be4cee");

  bool v = p.verify(pr, ch);

  std::cout << ch << std::endl
            << std::endl;
  std::cout << v << std::endl;
  std::cout << pr << std::endl;

  std::cout << proof.quality(c) << std::endl;
  retrieveOriginal("./plot", p);
}