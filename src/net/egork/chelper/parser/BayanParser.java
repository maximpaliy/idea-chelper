package net.egork.chelper.parser;

import com.intellij.openapi.project.Project;
import net.egork.chelper.checkers.PEStrictChecker;
import net.egork.chelper.task.StreamConfiguration;
import net.egork.chelper.task.Task;
import net.egork.chelper.task.test.Test;
import net.egork.chelper.task.test.TestType;
import org.apache.commons.lang.StringEscapeUtils;

import javax.swing.*;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author egorku@yandex-team.ru
 */
public class BayanParser implements Parser {
    @Override
    public Icon getIcon() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getName() {
        return "HackerRank";
    }

    @Override
    public void getContests(Project project, DescriptionReceiver receiver) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void parseContest(Project project, String id, DescriptionReceiver receiver) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Task parseTask(Project project, Description description, DescriptionReceiver receiver) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TestType defaultTestType() {
        return TestType.MULTI_NUMBER;
    }

    @Override
    public Collection<Task> parseTaskFromHTML(String html) {
        StringParser parser = new StringParser(html);
        try {
            parser.advance(true, "<div class=\"menu\">");
            parser.advance(true, "<a href=\"/en/contest/");
            String contestName = parser.advance(false, "/").replace('_', ' ');
            contestName = "Bayan " + Character.toUpperCase(contestName.charAt(0)) + contestName.substring(1);
            parser.advance(true, "ng-controller=\"ProblemController\">");
            parser.advance(true, "<h1>");
            String taskName = StringEscapeUtils.unescapeHtml(parser.advance(false, "</h1>").trim());
            String taskClass = CodeChefParser.getTaskID(taskName);
            StreamConfiguration input = new StreamConfiguration(StreamConfiguration.StreamType.LOCAL_REGEXP,
                "\\\\d*[.]in");
            List<Test> tests = new ArrayList<Test>();
            parser.advance(true, "<pre class=\"input-output\">");
            String testInput = parser.advance(false, "</pre>").trim() + "\n";
            parser.advance(true, "<pre class=\"input-output\">");
            String testOutput = parser.advance(false, "</pre>").trim() + "\n";
            tests.add(new Test(testInput, testOutput, tests.size()));
            parser.advance(true, "<form id=\"submit-form\"");
            parser.advance(true, "problem/");
            StreamConfiguration output = new StreamConfiguration(StreamConfiguration.StreamType.CUSTOM,
                parser.advance(false, "/").toLowerCase() + ".out");
            return Collections.singleton(new Task(taskName, defaultTestType(), input, output, tests.toArray(new Test[tests.size()]), null,
                "-Xmx1024M", "Main", taskClass, PEStrictChecker.class.getCanonicalName(), "",
                new String[0], null, contestName, true, null, null, false, false));
        } catch (ParseException e) {
            return Collections.emptyList();
        }
    }
}
