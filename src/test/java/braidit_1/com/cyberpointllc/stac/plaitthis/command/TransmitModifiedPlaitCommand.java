package braidit_1.com.cyberpointllc.stac.plaitthis.command;

import braidit_1.com.cyberpointllc.stac.communications.CommunicationsException;
import braidit_1.com.cyberpointllc.stac.plaitthis.PlaitIt;
import braidit_1.com.cyberpointllc.stac.plaitthis.phase.PlaitSelectedPhase;
import braidit_1.com.cyberpointllc.stac.plaitthis.phase.ChoicesPhase;
import braidit_1.com.cyberpointllc.stac.plaitthis.phase.GamePhase;
import braidit_1.com.cyberpointllc.stac.proto.Braidit.BraidItMessage;
import braidit_1.com.cyberpointllc.stac.proto.Braidit.ModifiedBraidMessage;
import org.apache.commons.cli.CommandLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import plv.colorado.edu.quantmchecker.qual.Bound;
import plv.colorado.edu.quantmchecker.qual.Inv;
import plv.colorado.edu.quantmchecker.qual.Summary;

import java.io.PrintStream;
import java.util.List;

public class TransmitModifiedPlaitCommand extends PlaitItCommand {
    private static final Logger logger = LoggerFactory.getLogger(TransmitModifiedPlaitCommand.class);
    private static final String COMMAND = "send_modified";
    private static final String USAGE = COMMAND;
    private final PlaitIt plaitIt;

    public TransmitModifiedPlaitCommand(PlaitIt plaitIt) {
        super(COMMAND, "Send the modified braid to the other player", USAGE);
        this.plaitIt = plaitIt;
    }

    @Override
    @Summary({"this.plaitIt.currentGame.currentRound.phases", "1"})
    public void execute(PrintStream out, CommandLine cmdLine) {
        @Bound("36") int i;
        @Inv("= phase.allowedCommands 12") GamePhase phase = plaitIt.getStep();
        logger.debug("Command {} in state {}", COMMAND, phase);
        if (!phase.matches(GamePhase.Phase.PLAIT_SELECTED)) {
            plaitIt.printUsrMsg("Command " + COMMAND + " is illegal in state " + plaitIt.getStep());
        } else if (phase instanceof PlaitSelectedPhase) {
            @Inv("= selectedPhase.allowedCommands 12") PlaitSelectedPhase selectedPhase = (PlaitSelectedPhase) phase;

            try {
                if (cmdLine.getArgList().size() != 0) {
                    plaitIt.printUsrMsg(USAGE);
                } else {
                    logger.info("Sending modified braid");
                    @Inv("= choices.allowedCommands 12") ChoicesPhase choices = plaitIt.obtainCurrentGame().getChoicesPhase();
                    choices.fixFinished(true);
                    ModifiedBraidMessage plaitMsg = ModifiedBraidMessage.newBuilder()
                            .setBraid(selectedPhase.getPlaitString())
                            .setBraid1(choices.fetchPlait(1).toString())
                            .setBraid2(choices.fetchPlait(2).toString())
                            .setBraid3(choices.fetchPlait(3).toString())
                            .setBraid4(choices.fetchPlait(4).toString())
                            .setBraid5(choices.fetchPlait(5).toString())
                            .build();

                    BraidItMessage msg = BraidItMessage.newBuilder()
                            .setType(BraidItMessage.Type.MODIFIED_BRAID)
                            .setBraidMsg(plaitMsg)
                            .build();
                    plaitIt.transmitMessage(msg.toByteArray());
                    plaitIt.setStep(new GamePhase(GamePhase.Phase.AWAIT_OUTCOME));
                }
            } catch (Exception e) {
                plaitIt.printUsrMsg("Problem processing command: " + e.getMessage());
            }
        } else {
            plaitIt.printUsrMsg("Problem processing command: Internal State is invalid.  It should be an instance of BriadSelectedState but is " + phase + " (" + phase.getClass().getName() + ")");
        }
    }
}
