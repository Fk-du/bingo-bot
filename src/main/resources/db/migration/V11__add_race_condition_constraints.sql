-- H-5: prevent double game join (same player, same game)
ALTER TABLE game_cards ADD CONSTRAINT uq_game_cards_game_player UNIQUE (game_id, player_id);

-- H-4: prevent double bingo claim (same player, same game, same result)
ALTER TABLE bingo_claims ADD CONSTRAINT uq_bingo_claims_game_player_result UNIQUE (game_id, player_id, result);
