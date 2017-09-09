package net.egork.chelper.task.test;


import net.egork.chelper.util.InputReader;
import net.egork.chelper.util.OutputWriter;

/**
 * @author Egor Kulikov (kulikov@devexperts.com)
 */
public class Test extends TestBase {
    public final String input;
    public final String output;
    public final boolean active;

    public Test(String input) {
        this(input, null);
    }

    public Test(String input, String output) {
        this(input, output, -1);
    }

    public Test(String input, String output, int index) {
        this(input, output, index, true);
    }

    public Test(String input, String output, int index, boolean active) {
        super(index);
        this.input = input;
        this.output = output;
        this.active = active;
    }

    @Override
    public String toString() {
        String inputRepresentation = input.replace('\n', ' ');
        inputRepresentation = inputRepresentation.length() > 15 ? inputRepresentation.substring(0, 12) + "..." :
            inputRepresentation;
        return "Test #" + index + ": " + inputRepresentation;
    }

    public Test setIndex(int index) {
        return new Test(input, output, index, active);
    }

    @Override
    public Test setActive(boolean active) {
        return new Test(input, output, index, active);
    }

    public void saveTest(OutputWriter out) {
        out.printLine(index);
        out.printString(input);
        out.printString(output);
        out.printBoolean(active);
    }

    public static Test loadTest(InputReader in) {
        int index = in.readInt();
        String input = in.readString();
        String output = in.readString();
        boolean active = in.readBoolean();
        return new Test(input, output, index, active);
    }
}
