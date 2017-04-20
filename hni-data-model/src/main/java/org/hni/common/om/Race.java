package org.hni.common.om;

// Generated Apr 19, 2017 11:39:46 PM by Hibernate Tools 3.6.0

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import static javax.persistence.GenerationType.IDENTITY;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * Race generated by hbm2java
 */
@Entity
@Table(name = "race")
public class Race implements java.io.Serializable {

	private Integer id;
	private String raceDesc;

	public Race() {
	}

	public Race(String raceDesc) {
		this.raceDesc = raceDesc;
	}

	@Id
	@GeneratedValue(strategy = IDENTITY)
	@Column(name = "id", unique = true, nullable = false)
	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	@Column(name = "race_desc", nullable = false, length = 50)
	public String getRaceDesc() {
		return this.raceDesc;
	}

	public void setRaceDesc(String raceDesc) {
		this.raceDesc = raceDesc;
	}

}