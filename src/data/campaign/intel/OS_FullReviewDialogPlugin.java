package data.campaign.intel;

import java.util.Map;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.util.Misc;

public class OS_FullReviewDialogPlugin implements InteractionDialogPlugin {
    private InteractionDialogAPI dialog;
    private String heading;
    private String fullReview;
    private String rating;
    private String proTip;

    public OS_FullReviewDialogPlugin(String heading, String fullReview) {
        this(heading, fullReview, null, null);
    }

    public OS_FullReviewDialogPlugin(String heading, String fullReview, String rating, String proTip) {
        this.heading = heading;
        this.fullReview = fullReview;
        this.rating = rating;
        this.proTip = proTip;
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        dialog.setOptionOnEscape("Close (Esc)", "leave");
        
        TextPanelAPI text = dialog.getTextPanel();
        
        if (heading != null) {
            LabelAPI hLabel = text.addPara(heading, Misc.getBasePlayerColor());
            hLabel.setHighlight(heading);
            hLabel.setHighlightColors(Misc.getBrightPlayerColor());
        }
        
        LabelAPI authLabel = text.addPara("Field Dispatch from Anton 'Bones' Burndain (Chief Mess Attendant, Ret.):", Misc.getGrayColor());
        authLabel.setHighlight("Anton 'Bones' Burndain");
        authLabel.setHighlightColors(Misc.getHighlightColor());
        
        if (fullReview != null) {
            String[] paragraphs = fullReview.split("\n\n");
            for (String p : paragraphs) {
                if (!p.trim().isEmpty()) {
                    text.addPara(p.trim());
                }
            }
        }
        
        if (rating != null && !rating.isEmpty()) {
            LabelAPI rLabel = text.addPara(rating, Misc.getHighlightColor());
            rLabel.setHighlightColor(Misc.getBrightPlayerColor());
        }
        
        if (proTip != null && !proTip.isEmpty()) {
            LabelAPI tipLabel = text.addPara(proTip, Misc.getGrayColor());
            tipLabel.setHighlight("Bones's Field Rule:");
            tipLabel.setHighlightColors(Misc.getHighlightColor());
        }
        
        OptionPanelAPI options = dialog.getOptionPanel();
        options.addOption("Close (Esc)", "leave");
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
    public Map<String, MemoryAPI> getMemoryMap() { return new java.util.HashMap<String, MemoryAPI>(); }
}
