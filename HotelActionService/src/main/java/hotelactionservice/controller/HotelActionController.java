package hotelactionservice.controller;

import hotelactionservice.dto.ActionRequest;
import hotelactionservice.dto.ActionResponse;
import hotelactionservice.service.ActionExecutorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/actions")
@RequiredArgsConstructor
public class HotelActionController {

    private final ActionExecutorService actionExecutorService;

    @PostMapping("/execute")
    public ResponseEntity<ActionResponse> executeAction(@RequestBody ActionRequest request) {
        ActionResponse response = actionExecutorService.processAction(
                request.getHotelKey(),
                request.getChatId(),
                request.getActionName(),
                request.getParameters()
        );
        return ResponseEntity.ok(response);
    }
}