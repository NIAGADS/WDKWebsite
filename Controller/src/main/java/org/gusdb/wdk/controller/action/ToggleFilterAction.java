package org.gusdb.wdk.controller.action;

import static org.gusdb.wdk.model.user.StepContainer.withId;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.gusdb.fgputil.validation.ValidationLevel;
import org.gusdb.wdk.controller.CConstants;
import org.gusdb.wdk.controller.actionutil.ActionUtility;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.answer.spec.AnswerSpec;
import org.gusdb.wdk.model.user.Step;
import org.gusdb.wdk.model.user.Strategy;
import org.gusdb.wdk.model.user.User;

public class ToggleFilterAction extends Action {

  public static final String PARAM_FILTER = "filter";
  public static final String PARAM_STEP = "step";
  public static final String PARAM_DISABLED = "disabled";

  public static final String ATTR_SUMMARY = "summary";

  private static final Logger LOG = Logger.getLogger(ToggleFilterAction.class);

  @Override
  public ActionForward execute(ActionMapping mapping, ActionForm form, HttpServletRequest request,
      HttpServletResponse response) throws Exception {
    LOG.debug("Entering ToggleFilterAction...");

    String filterName = request.getParameter(PARAM_FILTER);
    if (filterName == null)
      throw new WdkUserException("Required filter parameter is missing.");
    String stepIdStr = request.getParameter(PARAM_STEP);
    if (stepIdStr == null)
      throw new WdkUserException("Required step parameter is missing.");
    long stepId = Long.valueOf(stepIdStr);
    String strDisabled = request.getParameter(PARAM_DISABLED);
    if (strDisabled == null)
      throw new WdkUserException("Required disabled parameter is missing.");
    boolean disabled = Boolean.valueOf(strDisabled);

    WdkModel wdkModel = ActionUtility.getWdkModel(servlet).getModel();
    User user = ActionUtility.getUser(request).getUser();
    Step step = wdkModel.getStepFactory().getStepById(stepId)
        .orElseThrow(() -> new WdkUserException("No step exists with ID: " + stepId));
    if (step.getUser().getUserId() != user.getUserId()) {
      throw new WdkUserException("You do not have permission to modify this step.");
    }

    // before changing step, need to check if strategy is saved, if yes, make a copy.
    String strStrategyId = request.getParameter(CConstants.WDK_STRATEGY_ID_KEY);
    if (strStrategyId != null && !strStrategyId.isEmpty()) {
      long strategyId = Long.valueOf(strStrategyId.split("_", 2)[0]);
      Strategy strategy = wdkModel.getStepFactory().getStrategyById(strategyId)
          .orElseThrow(() -> new WdkUserException("No strategy exists with ID: " + strategyId));
      strategy.findFirstStep(withId(stepId)).orElseThrow(() ->
          new WdkUserException("Step " + stepId + " does not belong to strategy " + strategyId));
      if (strategy.isSaved()) {
        strategy.update(false);
      }
    }

    AnswerSpec newSpec = AnswerSpec.builder(step.getAnswerSpec())
      .replaceFirstFilterOption(filterName, oldFilter -> oldFilter.setDisabled(disabled))
      .build(step.getUser(), step.getContainer(), ValidationLevel.RUNNABLE);
    step.setAnswerSpec(newSpec);
    step.writeParamFiltersToDb();

    ActionForward showApplication = mapping.findForward(CConstants.SHOW_APPLICATION_MAPKEY);

    LOG.debug("Filter " + filterName + ": " + (disabled ? "disabled" : "enabled"));
    LOG.debug("Foward to " + CConstants.SHOW_APPLICATION_MAPKEY + ", " + showApplication);

    StringBuffer url = new StringBuffer(showApplication.getPath());
    //String state = request.getParameter(CConstants.WDK_STATE_KEY);
    //url.append("?state=" + FormatUtil.urlEncodeUtf8(state));

    ActionForward forward = new ActionForward(url.toString());
    forward.setRedirect(true);
    LOG.debug("Leaving ToggleFilterAction.");
    return forward;
  }
}
