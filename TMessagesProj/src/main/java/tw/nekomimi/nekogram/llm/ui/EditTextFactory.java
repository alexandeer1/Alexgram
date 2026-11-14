package tw.nekomimi.nekogram.llm.ui;

import android.content.Context;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;

public final class EditTextFactory {

    private EditTextFactory() {
    }

    public static EditTextBoldCursor createAndSetupEditText(
            Context context,
            Theme.ResourcesProvider resourcesProvider,
            String initialText,
            String hintText,
            int imeOptions,
            boolean requestFocus
    ) {
        return LlmEditTextFactory.createAndSetupEditText(context, resourcesProvider, initialText, hintText, imeOptions, requestFocus);
    }

    public static EditTextBoldCursor createAndSetupMultilineEditText(
            Context context,
            Theme.ResourcesProvider resourcesProvider,
            String initialText,
            String hintText,
            int imeOptions,
            boolean requestFocus
    ) {
        return LlmEditTextFactory.createAndSetupMultilineEditText(context, resourcesProvider, initialText, hintText, imeOptions, requestFocus);
    }
}
