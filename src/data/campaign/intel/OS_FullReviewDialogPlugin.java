package data.campaign.intel;

import java.util.Map;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.VisualPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;

public class OS_FullReviewDialogPlugin implements InteractionDialogPlugin {
    private InteractionDialogAPI dialog;
    private String heading;
    private String fullReview;

    public OS_FullReviewDialogPlugin(String heading, String fullReview) {
        this.heading = heading;
        this.fullReview = fullReview;
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        dialog.setOptionOnEscape("Leave", "leave");
        
        TextPanelAPI text = dialog.getTextPanel();
        text.addPara(heading);
        text.addPara(fullReview);
        
        OptionPanelAPI options = dialog.getOptionPanel();
        options.addOption("Close", "leave");
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if ("leave".equals(optionData)) {
            dialog.dismiss();
        }
    }

    @Override
    public void optionMousedOver(String optionText, Object optionData) {}
    @Override
    public void advance(float amount) {}
    @Override
    public void backFromEngagement(EngagementResultAPI battleResult) {}
    @Override
    public Object getContext() { return null; }
    @Override
    public Map<String, MemoryAPI> getMemoryMap() { return null; }
}
