package com.homie.app.controller;

import com.homie.app.dto.BillCreateDto;
import com.homie.app.entity.User;
import com.homie.app.service.BillService;
import com.homie.app.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Handles the Bills page: viewing all shared bills with each housemate's
 * paid/unpaid status, adding a new bill (which is split equally among
 * everyone), updating a bill's details, and marking shares as paid. Only
 * the housemate who added a bill can edit it, tick off payments, or
 * delete it — they are the one who is out of pocket and collecting money
 * back from everyone else.
 */
@Controller
public class BillController {

    // Fixed list of categories offered in the "Add a bill" form. Kept as a
    // simple constant list rather than an enum, matching how other fixed
    // house data (e.g. the cleaning order) is stored in this project.
    private static final List<String> CATEGORIES = List.of(
            "Electricity", "Gas", "Wifi", "Water", "Supplies", "Other"
    );

    private final BillService billService;
    private final UserService userService;

    public BillController(BillService billService, UserService userService) {
        this.billService = billService;
        this.userService = userService;
    }

    @GetMapping("/bills")
    public String bills(Model model, Authentication authentication) {
        User currentUser = userService.findByEmail(authentication.getName());
        addCommonAttributes(model, currentUser);

        if (!model.containsAttribute("billForm")) {
            model.addAttribute("billForm", new BillCreateDto());
        }

        return "bills"; // renders templates/bills.html
    }

    @PostMapping("/bills/create")
    public String createBill(@Valid @ModelAttribute("billForm") BillCreateDto dto,
                              BindingResult result,
                              Model model,
                              Authentication authentication) {

        User currentUser = userService.findByEmail(authentication.getName());

        if (result.hasErrors()) {
            addCommonAttributes(model, currentUser);
            return "bills";
        }

        billService.createBill(dto, currentUser);
        return "redirect:/bills";
    }

    @PostMapping("/bills/{billId}/update")
    public String updateBill(@PathVariable Long billId,
                              @RequestParam String description,
                              @RequestParam String category,
                              @RequestParam Double totalAmount,
                              @RequestParam LocalDate dueDate,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {

        String error = billService.updateBill(billId, description, category, totalAmount,
                dueDate, authentication.getName());
        if (error != null) {
            redirectAttributes.addFlashAttribute("billsError", error);
        }
        return "redirect:/bills";
    }

    @PostMapping("/bills/payment/{paymentId}/toggle")
    public String togglePayment(@PathVariable Long paymentId,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {

        String error = billService.togglePaid(paymentId, authentication.getName());
        if (error != null) {
            redirectAttributes.addFlashAttribute("billsError", error);
        }
        return "redirect:/bills";
    }

    @PostMapping("/bills/{billId}/delete")
    public String deleteBill(@PathVariable Long billId,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {

        String error = billService.deleteBill(billId, authentication.getName());
        if (error != null) {
            redirectAttributes.addFlashAttribute("billsError", error);
        }
        return "redirect:/bills";
    }

    // Shared between the GET page load and the "add bill" error path, so
    // both render the exact same stat cards and bill list.
    private void addCommonAttributes(Model model, User currentUser) {
        model.addAttribute("bills", billService.allBills());
        model.addAttribute("categories", CATEGORIES);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("currentUserId", currentUser.getId());
        model.addAttribute("outstandingTotal", billService.outstandingTotal());
        model.addAttribute("yourShareDue", billService.yourShareDue(currentUser.getId()));
        model.addAttribute("housemateCount", billService.housemateCount());
        model.addAttribute("todayDisplay",
                LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.ENGLISH)));
    }
}
