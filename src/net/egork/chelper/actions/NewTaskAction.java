package net.egork.chelper.actions;

import com.intellij.ide.actions.CreateElementActionBase;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import net.egork.chelper.task.Task;
import net.egork.chelper.ui.CreateTaskDialog;
import net.egork.chelper.util.FileUtils;
import net.egork.chelper.util.ProjectUtils;
import org.jetbrains.annotations.NotNull;

/**
 * @author Egor Kulikov (kulikov@devexperts.com)
 */
public class NewTaskAction extends CreateElementActionBase {
    @NotNull
    @Override
    protected PsiElement[] invokeDialog(Project project, PsiDirectory psiDirectory) {
        return create(null, psiDirectory);
    }

    protected void checkBeforeCreate(String s, PsiDirectory psiDirectory) throws IncorrectOperationException {
    }

    @NotNull
    @Override
    protected PsiElement[] create(String s, PsiDirectory psiDirectory) {
        return createTask(s, psiDirectory, null, true);
    }

    public static PsiElement[] createTask(String s, PsiDirectory psiDirectory, Task template, boolean allowNameChange) {
        if (!FileUtils.isJavaDirectory(psiDirectory))
            return PsiElement.EMPTY_ARRAY;
        Task task = CreateTaskDialog.showDialog(psiDirectory, s, template, allowNameChange);
        if (task == null)
            return PsiElement.EMPTY_ARRAY;
        PsiElement main = ProjectUtils.getPsiElement(psiDirectory.getProject(), task.taskClass);
        if (main == null)
            return PsiElement.EMPTY_ARRAY;
        ProjectUtils.createConfiguration(task, true, psiDirectory.getProject());
        return new PsiElement[]{main};
    }

    @Override
    protected String getErrorTitle() {
        return "Error";
    }

    @Override
    protected String getCommandName() {
        return "Task";
    }

    @Override
    protected String getActionName(PsiDirectory psiDirectory, String s) {
        return "New task " + s;
    }

    @Override
    protected boolean isAvailable(DataContext dataContext) {
        if (!ProjectUtils.isEligible(dataContext))
            return false;
        PsiDirectory directory = FileUtils.getDirectory(dataContext);
        return FileUtils.isJavaDirectory(directory);
    }
}
