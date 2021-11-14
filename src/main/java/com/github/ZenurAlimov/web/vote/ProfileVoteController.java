package com.github.ZenurAlimov.web.vote;

import com.github.ZenurAlimov.error.IllegalRequestDataException;
import com.github.ZenurAlimov.model.Restaurant;
import com.github.ZenurAlimov.model.Vote;
import com.github.ZenurAlimov.repository.RestaurantRepository;
import com.github.ZenurAlimov.repository.UserRepository;
import com.github.ZenurAlimov.repository.VoteRepository;
import com.github.ZenurAlimov.to.VoteTo;
import com.github.ZenurAlimov.util.VoteUtil;
import com.github.ZenurAlimov.web.AuthUser;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static com.github.ZenurAlimov.util.DateUtil.dayOrMax;
import static com.github.ZenurAlimov.util.DateUtil.dayOrMin;
import static com.github.ZenurAlimov.web.restaurant.RestaurantController.TODAY;

@RestController
@RequestMapping(value = ProfileVoteController.REST_URL, produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
@AllArgsConstructor
@Transactional(readOnly = true)
public class ProfileVoteController {

    static final String REST_URL = "/api/profile/votes";

    private final VoteRepository voteRepository;
    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;

    @GetMapping
    public List<VoteTo> getAll(@AuthenticationPrincipal AuthUser authUser) {
        log.info("get All Votes for user {}", authUser.id());
        return VoteUtil.getTos(voteRepository.getAll(authUser.id()));
    }

    @GetMapping("/by-restaurantId")
    public List<VoteTo> getAllByRestaurantId(@AuthenticationPrincipal AuthUser authUser, @RequestParam int restaurantId) {
        int userId = authUser.id();
        log.info("get Votes for user {} By Restaurant {}", userId, restaurantId);
        return VoteUtil.getTos(voteRepository.getByRestaurant(userId, restaurantId));
    }

    @GetMapping("/filter")
    public List<VoteTo> getBetween(@AuthenticationPrincipal AuthUser authUser,
                                   @RequestParam @Nullable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam @Nullable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        int userId = authUser.id();
        log.info("votes between dates({} - {}) for user {}", from, to, userId);
        return VoteUtil.getTos(voteRepository.getBetween(dayOrMin(from), dayOrMax(to), userId));
    }

    @Transactional
    @PostMapping
    public ResponseEntity<VoteTo> createOrUpdate(@AuthenticationPrincipal AuthUser authUser, @RequestParam int restaurantId) {
        int userId = authUser.id();
        Restaurant restaurant = restaurantRepository.getById(restaurantId);
        Vote vote = voteRepository.getByDate(userId, TODAY).orElse(
                new Vote(TODAY, userRepository.getById(userId), restaurant)
        );
        if (vote.isNew()) {
            log.info("vote saved for the restaurant {}", restaurantId);
            Vote created = voteRepository.save(vote);
            URI uriOfNewResource = ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path(REST_URL + "/{id}")
                    .buildAndExpand(created.getId()).toUri();
            return ResponseEntity.created(uriOfNewResource).body(VoteUtil.createTo(created));
        }
        if (LocalTime.now().isAfter(LocalTime.of(11, 0))) {
            throw new IllegalRequestDataException("Vote can only be changed before 11:00");
        }
        if (vote.getRestaurant().id() == restaurantId) {
            log.info("trying to vote for the restaurant {} again", restaurantId);
        } else {
            log.info("userId {} changed vote for the restaurant {}", userId, restaurantId);
            vote.setRestaurant(restaurant);
        }
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
