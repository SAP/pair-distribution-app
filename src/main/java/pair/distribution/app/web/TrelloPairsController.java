package pair.distribution.app.web;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pair.distribution.app.helpers.DayPairsHelper;
import pair.distribution.app.persistence.mongodb.TrelloPairsRepository;
import pair.distribution.app.trello.PairingBoard;
import pair.distribution.app.trello.entities.Company;
import pair.distribution.app.trello.entities.DayPairs;
import pair.distribution.app.trello.entities.DevPairCombinations;
import pair.distribution.app.trello.entities.Developer;
import pair.distribution.app.trello.entities.OpsPairCombinations;
import pair.distribution.app.trello.entities.Pair;
import pair.distribution.app.trello.entities.PairCombinations;



@RestController
public class TrelloPairsController {
   
    private static final Logger logger = LoggerFactory.getLogger(TrelloPairsController.class);
    
    private TrelloPairsRepository repository;
	@Value("${trello.api.token}")
	private String apiToken;
	@Value("${trello.api.key}")
	private String apiKey;
	@Value("${trello.pairing.board.id}")
	private String pairingBoardId;
	
	private String[] messages = { "Have a nice day!", "Happy pairing!", "Go go go!", "To the keyboards!" };

    @Autowired
    public TrelloPairsController(TrelloPairsRepository repository) {
        this.repository = repository;
    }

    @RequestMapping(value = "/pairs/trello", method = RequestMethod.GET)
    public String pairs(@RequestParam(value = "everyday", defaultValue="false") boolean everyday) throws IOException {
		generatePairs(0, everyday);
		return generateHtmlOutput();
    }

    @RequestMapping(value = "/pairs/trello/json", method = RequestMethod.GET)
    public DayPairs pairsJson(@RequestParam(value = "everyday", defaultValue="false") boolean everyday) {
	    return generatePairs(0, everyday);
    }

    @RequestMapping(value = "/pairs/test/trello", method = RequestMethod.GET)
    public DayPairs pairs(@RequestParam("days") int daysIntoFuture, @RequestParam(value = "everyday", defaultValue="false") boolean everyday ) {
    		return generatePairs(daysIntoFuture, everyday);
    }

	private DayPairs generatePairs(int daysIntoFuture, boolean everydayRotation) {
		PairingBoard pairingBoardTrello = new PairingBoard(apiToken, apiKey, pairingBoardId);
		pairingBoardTrello.syncTrelloBoardState();
		logger.info("Syncing state finished. Updating database state");
		DayPairsHelper pairsHelper = new DayPairsHelper(repository, everydayRotation);
		pairsHelper.updateDataBaseWithTrelloContent(pairingBoardTrello.getPastPairs());
		List<DayPairs> pastPairs = repository.findAll();
		PairCombinations pairCombination = new DevPairCombinations(pastPairs);
		OpsPairCombinations devOpsPairCombination = new OpsPairCombinations(pastPairs, daysIntoFuture);

		List<DayPairs> todayDevOpsPairs = generateTodayOpsPairs(pairingBoardTrello, pairsHelper, devOpsPairCombination,
				pairingBoardTrello.getDevs(), pairingBoardTrello.getDevOpsCompanies());
		DayPairs todayPairs = generateTodayDevPairs(pairingBoardTrello, pairsHelper, pairCombination,
				getTodayDevelopers(pairingBoardTrello, todayDevOpsPairs), !todayDevOpsPairs.isEmpty());
		todayDevOpsPairs.stream().forEach(devOpsPairs -> todayPairs.addPiars(devOpsPairs.getPairs()));

		pairingBoardTrello.addTodayPairsToBoard(todayPairs, daysIntoFuture);
		logger.info("Trello board has been updated");

		return todayPairs;
	}

	private String generateHtmlOutput() throws IOException {
		ClassLoader classLoader = getClass().getClassLoader();
		InputStream inputStream = classLoader.getResourceAsStream("output.html");
		String data = readFromInputStream(inputStream);
		Random rand = new Random();
		int selectedMessage = rand.nextInt(messages.length);
		return data.replaceAll("--message--", messages[selectedMessage]);
	}

	private String readFromInputStream(InputStream inputStream) throws IOException {
		StringBuilder builder = new StringBuilder();
		BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
		String line;
		while ((line = br.readLine()) != null) {
			builder.append(line);
			builder.append("\n");
		}

		return builder.toString();
	}

	private List<Developer> getTodayDevelopers(PairingBoard pairingBoardTrello, List<DayPairs> todayDevOpsPairs) {
		List<Developer> todayDevDevelopers = new ArrayList<>(pairingBoardTrello.getDevs());
		todayDevOpsPairs.stream().forEach(dayPairs -> dayPairs.getPairs().values().stream().forEach(pair -> todayDevDevelopers.removeAll(pair.getDevs())));
		return todayDevDevelopers;
	}

	private DayPairs generateTodayDevPairs(PairingBoard pairingBoardTrello, DayPairsHelper pairsHelper, PairCombinations pairCombination, List<Developer> todayDevs, boolean opsPair) {
		Map<Pair, Integer> pairsWeight = pairsHelper.buildPairsWeightFromPastPairing(pairCombination, todayDevs);
		pairsHelper.buildDevelopersPairingDays(pairCombination, todayDevs);
		pairsHelper.adaptPairsWeight(pairsWeight, todayDevs);
		logger.info("Pairs weight after adaptation: {}", pairsWeight);
		pairsHelper.buildDevelopersTracksWeightFromPastPairing(pairCombination, todayDevs);
		logger.info("Tracks are: {} today devs are: {}", pairingBoardTrello.getTracks(), todayDevs);
		DayPairs todayDevPairs = pairsHelper.generateNewDayPairs(pairingBoardTrello.getTracks(), todayDevs, pairCombination, pairsWeight, pairingBoardTrello.getCompanies());
		logger.info("Today pairs are: {}",  todayDevPairs);

		if(!opsPair) {
			Map<Pair, Integer> buildPairsWeight = pairsHelper.buildBuildPairsWeightFromPastPairing(pairCombination, todayDevs);
			Map<Pair, Integer> communityPairsWeight = pairsHelper.buildCommunityPairsWeightFromPastPairing(pairCombination, todayDevs);
			logger.info("CommunityPairs weight is: {} BuildPairs weight is: {}", communityPairsWeight, buildPairsWeight);
			pairsHelper.setBuildPair(todayDevPairs.getPairs().values(), buildPairsWeight);
			pairsHelper.setCommunityPair(todayDevPairs.getPairs().values(), communityPairsWeight);
			logger.info("After setting build pair pairs are: {}", todayDevPairs);
		}
		return todayDevPairs;
	}

	private List<DayPairs> generateTodayOpsPairs(PairingBoard pairingBoardTrello, DayPairsHelper pairsHelper, OpsPairCombinations devOpsPairCombination,
			List<Developer> todayDevs, List<Company> devOpsCompanies) {
		List<DayPairs> todayPairs = new ArrayList<>();

		for (Company company : devOpsCompanies) {
			List<Developer> companyDevs = company.getCompanyExperiencedDevs(todayDevs);
			logger.info("Company : {} devs are: {}", company.getName(), companyDevs);
			Map<Pair, Integer> companyDevOpsPairsWeight = pairsHelper.buildPairsWeightFromPastPairing(devOpsPairCombination, companyDevs);
			logger.info("DevOpsPairs weight for company: {} is {}", company.getName(), companyDevOpsPairsWeight);
			devOpsPairCombination.setCompany(company);
			DayPairs dayPairs = pairsHelper.generateNewDayPairs(Arrays.asList(company.getTrack()), companyDevs, devOpsPairCombination, companyDevOpsPairsWeight, pairingBoardTrello.getCompanies());
			dayPairs.getPairs().values().stream().forEach(pair -> { pair.setOpsPair(true); pair.setBuildPair(true); pair.setCommunityPair(true); });
			todayPairs.add(dayPairs);
			logger.info("Today DevOpsPairs for company: {} are {}", company.getName(), todayPairs);
		}
		
		return todayPairs;
	}
}
