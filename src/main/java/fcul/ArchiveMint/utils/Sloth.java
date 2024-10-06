package fcul.ArchiveMint.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class Sloth {


    public static HashMap<String,String> sloth(String data,int iterations){
        String action = "sloth";
        try {
            // Build the command
            String[] command = {
                    "python3", "src/main/sloth/sloth.py",
                    action,
                    data,
                    String.valueOf(iterations)
            };
            //System.out.println("Running command: " + String.join(" ", command));
            // Start the process
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            Process process = processBuilder.start();

            // Read the output
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            List<String> list = null;
            while ((line = reader.readLine()) != null) {
                output.append(line);
                line = line.replace("[", "").replace("]", "")
                        .replace("'","").replace(" ","");
                String[] split = line.split(",");
                list = Arrays.asList(split);
                break;
            }
            HashMap <String, String> map = new HashMap<>();
            map.put("w",list.get(0));//witness
            map.put("h",list.get(1));//hash
            map.put("data",list.get(2));//data
            // Wait for process to complete
            process.waitFor();

            // Print the output
            //System.out.println("Python script output: " + output.toString());
            return map;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static boolean verify(String data, String hash, String witness,Integer iterations){
        String action = "verify";
        try {
            // Build the command
            String[] command = {
                    "python3", "src/main/sloth/sloth.py",
                    action,
                    data,
                    hash,
                    witness,
                    String.valueOf(iterations)
            };
            //System.out.println("Running command: " + String.join(" ", command));
            // Start the process
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            Process process = processBuilder.start();

            // Read the output
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            boolean result = false;
            while ((line = reader.readLine()) != null) {
                output.append(line);
                if (line.equals("True")) {
                    result = true;
                }
                break;
            }

            // Wait for process to complete
            process.waitFor();
            // Print the output
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    public static void main(String[] args) {
        long time = System.currentTimeMillis();
        HashMap<String,String> sloth = sloth("test",30000);
        //time in milliseconds
        System.out.println(System.currentTimeMillis()-time);
        System.out.println(sloth);
        time = System.currentTimeMillis();

        boolean v = verify(sloth.get("data"),sloth.get("h"),sloth.get("w"),30000);
        System.out.println(v);
        System.out.println(System.currentTimeMillis()-time);

    }

}

