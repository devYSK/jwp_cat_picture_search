package com.ys.cat_picture.cat_image.service;

import static com.ys.cat_picture.cat_image.convert.CatImageConverter.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import net.jqwik.api.Arbitraries;

import com.ys.cat_picture.cat_breed.repository.CatBreedRepository;
import com.ys.cat_picture.cat_image.convert.CatImageConverter;
import com.ys.cat_picture.cat_image.domain.CatImage;
import com.ys.cat_picture.cat_image.dto.CatImageDetailResponse;
import com.ys.cat_picture.cat_image.dto.CatImageResponse;
import com.ys.cat_picture.cat_image.repository.CatImageRepository;
import com.ys.cat_picture.infra.client.CatApiClient;
import com.ys.cat_picture.infra.client.response.CatOneResponse;
import com.ys.cat_picture.support.MonkeyUtils;

import com.navercorp.fixturemonkey.FixtureMonkey;

@Sql(scripts = {"/sql/clean_up.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
@SpringBootTest(properties = {"cat_api.url=https://api.thecatapi.com"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {"spring.config.location=classpath:application-test.yml"})
class CatImageServiceTest {

	@Autowired
	private CatImageService catImageService;

	@MockBean
	private CatApiClient catApiClient;

	@Autowired
	private CatImageRepository catImageRepository;

	@Autowired
	private CatBreedRepository catBreedRepository;

	@DisplayName("랜덤이미지 50개를 반환한다.")
	@Transactional
	@Test
	void randomImages() {
		//given
		FixtureMonkey fixtureMonkey = MonkeyUtils.byConstructorProperties(true);

		List<CatOneResponse> responses = fixtureMonkey.giveMeBuilder(CatOneResponse.class)
			.sampleList(50);

		given(catApiClient.getRandomImages(50, true))
			.willReturn(responses);

		//when
		List<CatImageResponse> randomImages = catImageService.getRandomImages();

		//then
		assertThat(randomImages).hasSize(50);
	}

	@DisplayName("Id로 데이터베이스에 존재하는 이미지를 가져온다")
	@Test
	void getById_findDB() {
		//given
		FixtureMonkey fixtureMonkey = MonkeyUtils.byFieldReflection(true);

		String catImageId = "imageId";

		CatImage catImage = fixtureMonkey.giveMeBuilder(CatImage.class)
			.set("externalId", catImageId)
			.setNull("id")
			.setNull("breed.id")
			.sample();

		catBreedRepository.save(catImage.getBreed());
		catImageRepository.save(catImage);

		//when
		CatImageDetailResponse response = catImageService.getById(catImageId);
		//then
		assertThat(response.id()).isEqualTo(catImageId);

		verify(catApiClient, never()).getImageById(catImageId);
	}

	@DisplayName("Id가 데이터베이스에 존재하지 않으므로 api로 이미지를 가져온다")
	@Test
	void getById_findApi() {
		//given
		String catImageId = "imageId";
		FixtureMonkey fixtureMonkey = MonkeyUtils.byConstructorProperties(true);

		CatOneResponse catOneResponse = fixtureMonkey.giveMeBuilder(CatOneResponse.class)
			.set("id", catImageId)
			.sample();

		given(catApiClient.getImageById(catImageId))
			.willReturn(catOneResponse);

		//when
		CatImageDetailResponse response = catImageService.getById(catImageId);
		//then
		assertThat(response.id()).isEqualTo(catImageId);
		boolean present = catImageRepository.findByExternalId(catImageId).isPresent();
		assertThat(present).isTrue();
		verify(catApiClient).getImageById(catImageId);
	}

}