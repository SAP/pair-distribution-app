package pair.distribution.app.trello.entities;

import java.util.HashMap;
import java.util.Map;

public class Developer implements Comparable<Developer>{

	private String id;
	private Company company;
	private boolean newDeveloper;
	private boolean hasContext;
	private int pairingDays;
	private Map<String, Integer> trackWeights;

	public Developer(String id) {
		this.id = id;
		this.company = new Company("");
		this.newDeveloper = false;
		this.hasContext = false;
		this.trackWeights = new HashMap<>();
	}

	public String getId() {
		return id;
	}

	public void setCompany(Company company) {
		this.company = company;
	}
	
	public Company getCompany() {
		return company;
	}

	public boolean getNew() {
		return this.newDeveloper;
	}

	public void setNew(boolean newDeveloper) {
		this.newDeveloper = newDeveloper;
	}

	public int getPairingDays() {
		return pairingDays;
	}

	public void udpatePairingDays() {
		this.pairingDays = this.pairingDays + 1;
	}
	
	public void setPairingDays(int pairingDays) {
		this.pairingDays = pairingDays;
	}

	public int getTrackWeight(String track) {
		return this.trackWeights.getOrDefault(track, 0);
	}

	public void updateTrackWeight(String track) {
		this.trackWeights.put(track, getTrackWeight(track) + 1);
	}
	
	public void setTrackWeight(String track, int weight) {
		this.trackWeights.put(track, weight);
	}

	public boolean hasContext() {
		return hasContext;
	}
	
	public void setHasContext(boolean hasContext) {
		this.hasContext = hasContext;
	}
	
	@Override
	public int compareTo(Developer anotherDeveloper) {
		return this.getId().compareTo(anotherDeveloper.getId());
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Developer other = (Developer) obj;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id)) {
			return false;
		}
		return true;
	}
	
	@Override
	public String toString() {
		return id;
	}
}
