package pair.rotation.app.web;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pair.rotation.app.helpers.DayPairsHelper;
import pair.rotation.app.persistence.mongodb.TrelloPairsRepository;
import pair.rotation.app.trello.PairingBoard;
import pair.rotation.app.trello.entities.DayPairs;
import pair.rotation.app.trello.entities.DevPairCombinations;
import pair.rotation.app.trello.entities.Developer;
import pair.rotation.app.trello.entities.Pair;
import pair.rotation.app.trello.entities.PairCombinations;



@RestController
public class TrelloPairsController {
   
    private static final Logger logger = LoggerFactory.getLogger(TrelloPairsController.class);
    private boolean rotate_everyday = false;
    
    private TrelloPairsRepository repository;
	@Value("${trello.access.key}")
	private String accessKey;
	@Value("${trello.application.key}")
	private String applicationKey;
	@Value("${trello.pairing.board}")
	private String pairingBoardId;
	
    @Autowired
    public TrelloPairsController(TrelloPairsRepository repository) {
        this.repository = repository;
    }

    @RequestMapping(value = "/pairs/trello", method = RequestMethod.GET)
    public DayPairs pairs() {
    	return generatePairs(0);
    }
    
    @RequestMapping(value = "/pairs/test/trello", method = RequestMethod.GET)
    public DayPairs pairs(@RequestParam("days") int daysIntoFuture ) {
    	return generatePairs(daysIntoFuture);
    }
    
    @RequestMapping(value = "/pairs/rotate", method = RequestMethod.PUT)
    public void pairs(@RequestParam("everyday") boolean everyday ) {
    	rotate_everyday = everyday;
    }

	private DayPairs generatePairs(int daysIntoFuture) {
		PairingBoard pairingBoardTrello = new PairingBoard(accessKey, applicationKey, pairingBoardId);
		logger.info("Pairing board found. Syncing state now");
		pairingBoardTrello.syncTrelloBoardState();
		logger.info("Syncing state finished. Updating database state");
		DayPairsHelper pairsHelper = new DayPairsHelper(repository);
		pairsHelper.updateDataBaseWithTrelloContent(pairingBoardTrello.getPastPairs());
		List<DayPairs> pastPairs = repository.findAll();
		logger.info("Database state is: " + pastPairs.toString());
		DayPairs todayPairs = null;
		PairCombinations pairCombination = new DevPairCombinations(pastPairs);
		List<Developer> todayDevs = pairingBoardTrello.getDevs();
		Map<Pair, Integer> pairsWeight = pairsHelper.buildPairsWeightFromPastPairing(pairCombination, todayDevs);
		logger.info("Pairs weight is:" + pairsWeight);
		logger.info("Building build pairs weight");
		Map<Pair, Integer> buildPairsWeight = pairsHelper.buildBuildPairsWeightFromPastPairing(pairCombination, todayDevs);
		logger.info("BuildPairs weight is:" + buildPairsWeight);
		Map<Pair, Integer> communityPairsWeight = pairsHelper.buildCommunityPairsWeightFromPastPairing(pairCombination, todayDevs);
		logger.info("CommunityPairs weight is:" + communityPairsWeight);
		pairsHelper.adaptPairsWeight(pairsWeight, todayDevs);
		logger.info("Pairs weight after DoD adaptation:" + pairsWeight);
		todayPairs = pairsHelper.generateNewDayPairs(pairingBoardTrello.getTracks(), todayDevs, pairCombination, pairsWeight, rotate_everyday);
		logger.info("Today pairs are: " + todayPairs);
		pairsHelper.rotateSoloPairIfAny(todayPairs, pairCombination, pairsWeight);
		logger.info("After single pair rotation they are: " + todayPairs);
		logger.info("Setting BuildPair");
		pairsHelper.setBuildPair(todayPairs.getPairs().values(), buildPairsWeight);
		logger.info("Setting CommunityPair");
		pairsHelper.setCommunityPair(todayPairs.getPairs().values(), buildPairsWeight);
		logger.info("After setting build pair pairs are: " + todayPairs);
		pairingBoardTrello.addTodayPairsToBoard(todayPairs, daysIntoFuture);
		logger.info("Trello board has been updated");
		return todayPairs;
	}
}