package com.homie.app.controller;

import com.homie.app.entity.House;
import com.homie.app.entity.Room;
import com.homie.app.entity.User;
import com.homie.app.repository.RoomRepository;
import com.homie.app.service.HouseService;
import com.homie.app.service.ScheduleService;
import com.homie.app.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Handles the "Manage house" page: every housemate can see their house's
 * invite code (to share with someone moving in) and its room/rota setup,
 * but only the house's owner (whoever created it - see User.isHouseOwner)
 * can actually change anything here. HouseService enforces that
 * permission itself, so this controller just surfaces whatever error it
 * returns rather than re-checking ownership.
 */
@Controller
public class HouseController {

    private final HouseService houseService;
    private final UserService userService;
    private final ScheduleService scheduleService;
    private final RoomRepository roomRepository;

    public HouseController(HouseService houseService, UserService userService,
                            ScheduleService scheduleService, RoomRepository roomRepository) {
        this.houseService = houseService;
        this.userService = userService;
        this.scheduleService = scheduleService;
        this.roomRepository = roomRepository;
    }

    @GetMapping("/house")
    public String house(Model model, Authentication authentication) {
        User currentUser = userService.findByEmail(authentication.getName());
        House house = currentUser.getHouse();

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("house", house);
        model.addAttribute("rooms", scheduleService.orderedRooms(house));
        model.addAttribute("members", scheduleService.orderedMembers(house));
        model.addAttribute("maxRooms", HouseService.MAX_ROOMS);
        model.addAttribute("minRooms", HouseService.MIN_ROOMS);

        return "house"; // renders templates/house.html
    }

    @PostMapping("/house/rename")
    public String renameHouse(@RequestParam String name,
                               Authentication authentication,
                               RedirectAttributes redirectAttributes) {
        House house = userService.findByEmail(authentication.getName()).getHouse();
        String error = houseService.renameHouse(house, name, authentication.getName());
        if (error != null) {
            redirectAttributes.addFlashAttribute("houseError", error);
        }
        return "redirect:/house";
    }

    @PostMapping("/house/rooms/add")
    public String addRoom(Authentication authentication, RedirectAttributes redirectAttributes) {
        House house = userService.findByEmail(authentication.getName()).getHouse();
        String error = houseService.addRoom(house, authentication.getName());
        if (error != null) {
            redirectAttributes.addFlashAttribute("houseError", error);
        }
        return "redirect:/house";
    }

    @PostMapping("/house/rooms/{roomId}/rename")
    public String renameRoom(@PathVariable Long roomId,
                              @RequestParam String name,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null) {
            redirectAttributes.addFlashAttribute("houseError", "That room no longer exists.");
            return "redirect:/house";
        }
        String error = houseService.renameRoom(room, name, authentication.getName());
        if (error != null) {
            redirectAttributes.addFlashAttribute("houseError", error);
        }
        return "redirect:/house";
    }

    @PostMapping("/house/rooms/{roomId}/remove")
    public String removeRoom(@PathVariable Long roomId,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null) {
            redirectAttributes.addFlashAttribute("houseError", "That room no longer exists.");
            return "redirect:/house";
        }
        String error = houseService.removeRoom(room, authentication.getName());
        if (error != null) {
            redirectAttributes.addFlashAttribute("houseError", error);
        }
        return "redirect:/house";
    }

    // Moves one housemate up or down the cleaning rota order.
    @PostMapping("/house/rota/move")
    public String moveInRota(@RequestParam Long userId,
                              @RequestParam String direction,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        House house = userService.findByEmail(authentication.getName()).getHouse();
        String error = houseService.moveMember(house, userId, "up".equalsIgnoreCase(direction),
                authentication.getName());
        if (error != null) {
            redirectAttributes.addFlashAttribute("houseError", error);
        }
        return "redirect:/house";
    }
}
