package net.egork.chelper.ui;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiDirectory;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import net.egork.chelper.ProjectData;
import net.egork.chelper.parser.Description;
import net.egork.chelper.parser.DescriptionReceiver;
import net.egork.chelper.parser.Parser;
import net.egork.chelper.parser.ParserTask;
import net.egork.chelper.task.Task;
import net.egork.chelper.task.test.TestType;
import net.egork.chelper.util.ExecuteUtils;
import net.egork.chelper.util.FileUtils;
import net.egork.chelper.util.Messenger;
import net.egork.chelper.util.ProjectUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Egor Kulikov (kulikov@devexperts.com)
 */
@SuppressWarnings("unchecked")
public class ParseDialog extends JDialog {
    private final static Logger LOG = Logger.getInstance(ParseDialog.class);
    private Collection<Task> result = Collections.emptyList();
    private JBList contestList;
    private JBList taskList;
    private JComboBox parserCombo;
    private JComboBox testType;
    private DirectorySelector location;
    private FileSelector template;
    private JTextField date;
    private JTextField contestName;
    private JCheckBox truncate;
    private ParseListModel contestModel;
    private ParseListModel taskModel;
    private Receiver taskReceiver;
    private Receiver contestReceiver;
    private int width = new JTextField(20).getPreferredSize().width;
    private Project project;

    private ParseDialog(final Project project) {
        super(null, "ParseProgresser Contest", ModalityType.APPLICATION_MODAL);
        this.project = project;
        setIconImage(ProjectUtils.iconToImage(IconLoader.getIcon("/icons/parseContest.png")));
        ProjectData data = ProjectUtils.getData(project);
        OkCancelPanel contentPanel = new OkCancelPanel(new BorderLayout(5, 5)) {
            @Override
            public void onOk() {
                try {
                    ProgressManager.getInstance().run(
                        //just use for dialog.
                        new com.intellij.openapi.progress.Task.WithResult<Object, Exception>(project, "Parse Tasks", true) {
                            private boolean canceled = false;

                            @Override
                            public void onCancel() {
                                this.canceled = true;
                            }

                            @Override
                            protected Object compute(@NotNull final ProgressIndicator indicator) throws Exception {
                                ExecuteUtils.executeWriteCommandAction(project, new Runnable() {
                                    @Override
                                    public void run() {
                                        if (!ParseDialog.this.isValidData(project)) return;
                                        contestReceiver.stop();
                                        List<Task> list = new ArrayList<Task>();
                                        Object[] tasks = taskList.getSelectedValues();
                                        Parser parser = (Parser) parserCombo.getSelectedItem();
                                        ProjectData data = ProjectUtils.getData(project);
                                        int finishedSum = 0;
                                        for (Object taskDescription : tasks) {
                                            if (canceled) return;
                                            indicator.setFraction(++finishedSum / tasks.length);
                                            Description description = (Description) taskDescription;
                                            Task raw = parser.parseTask(project, new Receiver() {
                                                @Override
                                                protected void processNewDescriptions(Collection<Description> descriptions) {
                                                    //ignore
                                                }
                                            }, description);
                                            if (raw == null) {
                                                Messenger.publishMessage("Unable to parse task " + description.description +
                                                    ". Connection problems or format change", NotificationType.ERROR);
                                                continue;
                                            }
                                            raw = raw.setInputOutputClasses(data.inputClass, data.outputClass);
                                            raw = raw.setTemplate(template.getText());
                                            Task task = new Task(raw.name, (TestType) testType.getSelectedItem(), raw.input, raw.output,
                                                raw.tests, location.getText(), raw.vmArgs, raw.mainClass,
                                                FileUtils.createIfNeeded(project, raw, raw.taskClass, location.getText()), raw.checkerClass,
                                                raw.checkerParameters, raw.testClasses, date.getText(), contestName.getText(),
                                                truncate.isSelected(), data.inputClass, data.outputClass, raw.includeLocale,
                                                data.failOnIntegerOverflowForNewTasks, raw.template);
                                            list.add(task);
                                        }
                                        if (canceled) return;
                                        result = list;
                                        if (!result.isEmpty()) {
                                            ProjectUtils.updateDefaultTask(result.iterator().next());
                                        }
                                        ParseDialog.this.setVisible(false);
                                        ProjectUtils.setDefaultParser(parser);
                                    }
                                });
                                return null;
                            }
                        }
                    );
                } catch (Exception ignored) {
                }
            }

            @Override
            public void onCancel() {
                ParseDialog.this.setVisible(false);
            }
        };

        JPanel upperPanel = new JPanel(new BorderLayout(5, 5));
        parserCombo = new ComboBox(Parser.PARSERS);
        parserCombo.setRenderer(new ListCellRendererWrapper() {
            public void customize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
                Parser parser = (Parser) value;
                JLabel label = new JLabel(parser.getName(), parser.getIcon(), JLabel.LEFT);
                label.setOpaque(true);
                if (selected)
                    label.setBackground(UIManager.getColor("textHighlight"));
            }
        });
        parserCombo.setSelectedItem(ProjectUtils.getDefaultParser());
        parserCombo.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                testType.setSelectedItem(((Parser) parserCombo.getSelectedItem()).defaultTestType());
                refresh();
            }
        });
        upperPanel.add(parserCombo, BorderLayout.CENTER);
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                refresh();
            }
        });
        upperPanel.add(refresh, BorderLayout.EAST);
        contentPanel.add(upperPanel, BorderLayout.NORTH);
        JPanel middlePanel = new JPanel(new GridLayout(1, 2, 5, 5)) {
            @Override
            public Dimension getPreferredSize() {
                Dimension size = super.getPreferredSize();
                size.height = 3 * width / 2;
                return size;
            }
        };
        contestModel = new ParseListModel();
        contestList = new JBList(contestModel);
        contestList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        contestList.setLayoutOrientation(JList.VERTICAL);
        contestList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting()) return;
                LOG.info("valueChanged: " + taskReceiver);
                if (taskReceiver != null) {
                    taskReceiver.stop();
                    taskReceiver = null;
                }
                Parser parser = (Parser) parserCombo.getSelectedItem();
                Description contest = (Description) contestList.getSelectedValue();
                if (contest == null) {
                    contestName.setText("");
                    return;
                }
                ParserTask.parserTask(project, contest.id, taskReceiver = new Receiver() {
                    @Override
                    protected void processNewDescriptions(final Collection<Description> descriptions) {
                        final Receiver receiver = this;
                        final boolean shouldMark = firstTime;
                        ExecuteUtils.executeStrictWriteAction(new Runnable() {
                            @Override
                            public void run() {
                                if (taskReceiver != receiver)
                                    return;
                                int was = taskModel.getSize();
                                taskModel.add(descriptions);
                                if (shouldMark) {
                                    int[] toMark = new int[taskModel.getSize() - was];
                                    for (int i = 0; i < toMark.length; i++)
                                        toMark[i] = was + i;
                                    taskList.setSelectedIndices(toMark);
                                }
                            }
                        });
                    }
                }, parser);
                taskModel.removeAll();
                contestName.setText(contest.description);
            }
        });
        JScrollPane contestScroll = new JBScrollPane(contestList, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        middlePanel.add(contestScroll);
        taskModel = new ParseListModel();
        taskList = new JBList(taskModel);
        taskList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        taskList.setLayoutOrientation(JList.VERTICAL);
        JScrollPane taskScroll = new JBScrollPane(taskList, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        middlePanel.add(taskScroll);
        contentPanel.add(middlePanel, BorderLayout.CENTER);
        JPanel bottomPanel = new JPanel(new GridLayout(1, 2, 5, 5));
        JPanel leftPanel = new JPanel(new VerticalFlowLayout()) {
            @Override
            public Dimension getPreferredSize() {
                Dimension size = super.getPreferredSize();
                size.width = width;
                return size;
            }
        };
        Task defaultTask = ProjectUtils.getDefaultTask();
        leftPanel.add(new JLabel("Test type:"));
        testType = new ComboBox(TestType.values());
        testType.setSelectedItem(ProjectUtils.getDefaultParser().defaultTestType());
        leftPanel.add(testType);
        leftPanel.add(new JLabel("Location:"));
        location = new DirectorySelector(project, data.defaultDirectory);
        leftPanel.add(location);
        leftPanel.add(new JLabel("Template:"));
        template = new FileSelector(project, defaultTask.template, "template", false);
        leftPanel.add(template);
        truncate = new JCheckBox("Truncate long tests", defaultTask.truncate);
        bottomPanel.add(leftPanel);
        JPanel rightPanel = new JPanel(new BorderLayout());
        JPanel dateAndContestName = new JPanel(new VerticalFlowLayout()) {
            @Override
            public Dimension getPreferredSize() {
                Dimension size = super.getPreferredSize();
                size.width = width;
                return size;
            }
        };
        dateAndContestName.add(new JLabel("Date:"));
        date = new JTextField(Task.getDateString());
        dateAndContestName.add(date);
        dateAndContestName.add(new JLabel("Contest name:"));
        contestName = new JTextField();
        dateAndContestName.add(contestName);
        rightPanel.add(dateAndContestName, BorderLayout.NORTH);
        rightPanel.add(truncate);
        JPanel buttonPanel = new JPanel(new GridLayout(1, 2));
        buttonPanel.add(contentPanel.getOkButton());
        buttonPanel.add(contentPanel.getCancelButton());
        rightPanel.add(buttonPanel, BorderLayout.SOUTH);
        bottomPanel.add(rightPanel);
        contentPanel.add(bottomPanel, BorderLayout.SOUTH);
        setContentPane(contentPanel);
        refresh();
        pack();
        Point center = ProjectUtils.getLocation(project, contentPanel.getSize());
        setLocation(center);
        setVisible(true);
    }

    private boolean isValidData(Project project) {
        PsiDirectory directory = FileUtils.getPsiDirectory(project, ParseDialog.this.location.getText());
        if (directory == null || !directory.isValid()) {
            Messenger.publishMessageWithBalloon(project, location.getTextField(), "invalid defaultDirectory!", MessageType.ERROR);
            return false;
        }
        return true;
    }

    private void refresh() {
        if (contestReceiver != null) {
            contestReceiver.stop();
            contestReceiver = null;
        }
        if (taskReceiver != null) {
            taskReceiver.stop();
            taskReceiver = null;
        }
        Parser parser = (Parser) parserCombo.getSelectedItem();
        final Description description = (Description) contestList.getSelectedValue();
        contestModel.removeAll();
        taskModel.removeAll();
        contestName.setText("");
        ParserTask.parserTask(project, null, contestReceiver = new Receiver() {
            @Override
            protected void processNewDescriptions(final Collection<Description> descriptions) {
                LOG.info("START ParserTask");
                final Receiver receiver = this;
                ExecuteUtils.executeStrictWriteActionAndWait(new Runnable() {
                    @Override
                    public void run() {
                        if (contestReceiver != receiver)
                            return;
                        boolean shouldMark = contestModel.getSize() == 0;
                        contestModel.add(descriptions);
                        if (shouldMark) {
                            for (Description contest : descriptions) {
                                if (description != null && description.id.equals(contest.id)) {
                                    contestList.setSelectedValue(contest, true);
                                    return;
                                }
                            }
                            if (contestModel.getSize() > 0)
                                contestList.setSelectedIndex(0);
                        }
                    }
                });
                LOG.info("END ParserTask");
            }
        }, parser);
        pack();
    }

    public static Collection<Task> parseContest(Project project) {
        ParseDialog dialog = new ParseDialog(project);
        return dialog.result;
    }

    private static class ParseListModel extends AbstractListModel {
        private List<Description> list = new ArrayList<Description>();

        public int getSize() {
            return list.size();
        }

        public Object getElementAt(int index) {
            return list.get(index);
        }

        public void removeAll() {
            int size = getSize();
            if (size == 0)
                return;
            list.clear();
            fireIntervalRemoved(this, 0, size - 1);
        }

        public void add(Collection<Description> collection) {
            if (collection.isEmpty())
                return;
            int size = getSize();
            list.addAll(collection);
            fireIntervalAdded(this, size, getSize() - 1);
        }
    }

    private abstract class Receiver implements DescriptionReceiver {
        private boolean stopped;
        public boolean firstTime = true;

        public void receiveDescriptions(Collection<Description> descriptions) {
            processNewDescriptions(descriptions);
            firstTime = false;
        }

        protected abstract void processNewDescriptions(Collection<Description> descriptions);

        public boolean isStopped() {
            return stopped;
        }

        public void stop() {
            stopped = true;
        }
    }
}
