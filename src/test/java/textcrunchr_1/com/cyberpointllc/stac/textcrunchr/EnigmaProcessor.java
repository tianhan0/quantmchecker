package textcrunchr_1.com.cyberpointllc.stac.textcrunchr;

import plv.colorado.edu.quantmchecker.qual.Inv;
import textcrunchr_1.com.ahancock.enigma.EnigmaFactory;
import textcrunchr_1.com.ahancock.enigma.EnigmaMachine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class EnigmaProcessor extends Processor {

    private static final String NAME = "enigma";

    public TCResult process(InputStream inps) throws IOException {
        // read to string
        InputStreamReader is = new InputStreamReader(inps);
        @Inv("br+<self>=+EnigmaProcessor23-EnigmaProcessor21-EnigmaProcessor24") StringBuilder sb = new StringBuilder();
        BufferedReader br = new BufferedReader(is);
        String read;
        EnigmaProcessor21: read = br.readLine();
        while (read != null) {
            EnigmaProcessor23: sb.append(read);
            EnigmaProcessor24: read = br.readLine();
        }
        String theString = sb.toString().toUpperCase();
        // Construct the machine
        EnigmaMachine machine = EnigmaFactory.buildEnigmaMachine();
        // Set the rotors, encrypt the string and print the results
        machine.setRotors(5, 9, 14);
        String encodedString = machine.encodeLine(theString);
        String name = "Enigma transformation (5, 9, 14)";
        String value = encodedString;
        return new TCResult(name, value);
    }

    public String getName() {
        return NAME;
    }
}