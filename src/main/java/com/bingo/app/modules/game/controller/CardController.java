package com.bingo.app.modules.game.controller;

import com.bingo.app.modules.game.dto.CardResponse;
import com.bingo.app.modules.game.service.BingoCardImageGenerator;
import com.bingo.app.modules.game.service.CardService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cards")
@RequiredArgsConstructor
public class CardController {

    private final CardService cardService;
    private final BingoCardImageGenerator bingoCardImageGenerator;

    @GetMapping("/{id}")
    public ResponseEntity<CardResponse> getCard(@PathVariable Long id) {
        return ResponseEntity.ok(cardService.findCardById(id));
    }

    @GetMapping("/available")
    public ResponseEntity<Page<CardResponse>> getAvailableCards(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(cardService.listAvailableCards(page, size));
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> getCardImage(@PathVariable Long id) {
        CardResponse card = cardService.findCardById(id);
        byte[] png = bingoCardImageGenerator.generatePng(card.numbers());
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(png);
    }
}
