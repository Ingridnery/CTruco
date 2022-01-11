/*
 *  Copyright (C) 2021 Lucas B. R. de Oliveira - IFSP/SCL
 *  Contact: lucas <dot> oliveira <at> ifsp <dot> edu <dot> br
 *
 *  This file is part of CTruco (Truco game for didactic purpose).
 *
 *  CTruco is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  CTruco is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with CTruco.  If not, see <https://www.gnu.org/licenses/>
 */

package com.bueno.domain.entities.game;

import com.bueno.domain.entities.deck.Card;
import com.bueno.domain.entities.player.util.Player;

import java.util.*;

public class Hand {

    private final Card vira;
    private final List<Card> dealtCards;
    private final List<Card> openCards;
    private final List<Round> roundsPlayed;
    private final List<Intel> history;
    private EnumSet<PossibleAction> possibleActions;

    private Player firstToPlay;
    private Player lastToPlay;
    private Player currentPlayer;
    private Player lastBetRaiser;

    private Card cardToPlayAgainst;
    private HandScore score;
    private HandScore scoreProposal;

    private HandResult result;
    private HandState state;

    Hand(Player firstToPlay, Player lastToPlay, Card vira){
        this.firstToPlay = Objects.requireNonNull(firstToPlay);
        this.lastToPlay = Objects.requireNonNull(lastToPlay);
        this.vira = Objects.requireNonNull(vira);

        dealtCards = new ArrayList<>();
        dealtCards.add(vira);
        dealtCards.addAll(firstToPlay.getCards());
        dealtCards.addAll(lastToPlay.getCards());

        score = HandScore.ONE;
        roundsPlayed = new ArrayList<>();
        openCards = new ArrayList<>();
        history = new ArrayList<>();

        addOpenCard(vira);

        if(isMaoDeOnze()){
            currentPlayer = firstToPlay.getScore() == 11 ? firstToPlay : lastToPlay;
            state = new WaitingMaoDeOnzeState(this);
        } else{
            currentPlayer = firstToPlay;
            state = new NoCardState(this);
        }
        updateHistory(PossibleAction.PLAY);
    }

    public void playFirstCard(Player player, Card card){
        final Player requester = Objects.requireNonNull(player, "Player must not be null!");
        final Card requesterCard = Objects.requireNonNull(card, "Card must not be null!");
        validateRequest(requester, PossibleAction.PLAY);
        state.playFirstCard(requester, requesterCard);
        updateHistory(PossibleAction.PLAY);
    }

    public void playSecondCard(Player player, Card cards){
        final Player requester = Objects.requireNonNull(player, "Player must not be null!");
        final Card requesterCard = Objects.requireNonNull(cards, "Card must not be null!");
        validateRequest(requester, PossibleAction.PLAY);
        state.playSecondCard(requester,requesterCard);
        updateHistory(PossibleAction.PLAY);
    }

    public void raise(Player requester){
        final Player player = Objects.requireNonNull(requester, "Player must not be null!");
        validateRequest(requester, PossibleAction.RAISE);
        state.raiseBet(player);
        updateHistory(PossibleAction.RAISE);
    }

    public void accept(Player responder){
        final Player player = Objects.requireNonNull(responder, "Player must not be null!");
        validateRequest(player, PossibleAction.ACCEPT);
        state.accept(player);
        updateHistory(PossibleAction.ACCEPT);
    }

    public void quit(Player responder){
        final Player player = Objects.requireNonNull(responder, "Player must not be null!");
        validateRequest(player, PossibleAction.QUIT);
        state.quit(player);
        updateHistory(PossibleAction.QUIT);
    }

    private void validateRequest(Player requester, PossibleAction action){
        if(!requester.equals(currentPlayer))
            throw new IllegalArgumentException(requester + " can not " + action + " in " + currentPlayer + " turn.");
        if(!possibleActions.contains(action))
            throw new IllegalStateException("Can not " + action + ", but " + possibleActions + ".");
    }

    void playRound(Card lastCard){
        final Round round = new Round(firstToPlay, cardToPlayAgainst, lastToPlay, lastCard, vira);
        round.play2();
        roundsPlayed.add(round);
    }

    boolean isMaoDeOnze() {
        return firstToPlay.getScore() == 11 ^ lastToPlay.getScore() == 11;
    }

    void addScoreProposal() {
        scoreProposal = score.increase();
    }

    boolean canRaiseBet(){
        return isPlayerAllowedToRaise() && isAllowedToRaise() && isAllowedToReRaise();
    }

    private boolean isPlayerAllowedToRaise() {
        return currentPlayer != lastBetRaiser;
    }

    private boolean isAllowedToRaise() {
        return score.get() < 12
                && score.increase().get() <= getMaxHandScore()
                && firstToPlay.getScore() < 11
                && lastToPlay.getScore() < 11;
    }

    private boolean isAllowedToReRaise() {
        return scoreProposal == null || scoreProposal.get() < 12 && scoreProposal.increase().get() <= getMaxHandScore();
    }

    void removeScoreProposal(){
        scoreProposal = null;
    }

    HandScore getScoreProposal() {
        return scoreProposal;
    }

    private int getMaxHandScore(){
        final int firstToPlayScore = firstToPlay.getScore();
        final int lastToPlayScore = lastToPlay.getScore();

        final int scoreToLosingPlayerWin = Player.MAX_SCORE - Math.min(firstToPlayScore, lastToPlayScore);

        return scoreToLosingPlayerWin % 3 == 0 ? scoreToLosingPlayerWin
                : scoreToLosingPlayerWin + (3 - scoreToLosingPlayerWin % 3);
    }

    private void updateHistory(PossibleAction action) {
        history.add(Intel.ofHand(this, action));
    }

    Intel getIntel(){
        return history.get(history.size() - 1);
    }

    List<Intel> getHistory(){
        return List.copyOf(history);
    }

    void defineRoundPlayingOrder() {
        getLastRoundWinner().filter(lastToPlay::equals).ifPresent(unused -> changePlayingOrder());
        currentPlayer = firstToPlay;
    }

    private void changePlayingOrder() {
        Player referenceHolder = firstToPlay;
        firstToPlay = lastToPlay;
        lastToPlay = referenceHolder;
    }

    Player getOpponentOf(Player player){
        return player.equals(firstToPlay)? lastToPlay : firstToPlay;
    }

    void checkForWinnerAfterSecondRound() {
        Optional<Player> firstRoundWinner = roundsPlayed.get(0).getWinner();
        Optional<Player> secondRoundWinner = roundsPlayed.get(1).getWinner();

        if (firstRoundWinner.isEmpty() && secondRoundWinner.isPresent())
            result = new HandResult(secondRoundWinner.get(), score);
        else if (firstRoundWinner.isPresent() && secondRoundWinner.isEmpty())
            result =  new HandResult(firstRoundWinner.get(), score);
        else if (secondRoundWinner.isPresent() && secondRoundWinner.get().equals(firstRoundWinner.get()))
            result = new HandResult(secondRoundWinner.get(), score);
    }

    void checkForWinnerAfterThirdRound() {
        Optional<Player> firstRoundWinner = roundsPlayed.get(0).getWinner();
        Optional<Player> lastRoundWinner = roundsPlayed.get(2).getWinner();

        if (lastRoundWinner.isEmpty() && firstRoundWinner.isPresent())
            result = new HandResult(firstRoundWinner.get(), score);
        else
            result = lastRoundWinner.map(player -> new HandResult(player, score)).orElseGet(HandResult::new);
    }

    void setCardToPlayAgainst(Card cardToPlayAgainst) {
        this.cardToPlayAgainst = cardToPlayAgainst;
    }

    public Optional<Card> getCardToPlayAgainst() {
        return Optional.ofNullable(cardToPlayAgainst);
    }

    void addOpenCard(Card card){
        if(!dealtCards.contains(card) && !card.equals(Card.closed()))
            throw new GameRuleViolationException("Card has not been dealt in this hand.");

        if(openCards.contains(card) && !card.equals(Card.closed()) )
            throw new GameRuleViolationException("Card " + card + " has already been played during hand.");

        openCards.add(card);
    }

    Optional<Player> getLastRoundWinner(){
        if(roundsPlayed.isEmpty()) return Optional.empty();
        return roundsPlayed.get(roundsPlayed.size() - 1).getWinner();
    }

    List<Round> getRoundsPlayed() {
        return new ArrayList<>(roundsPlayed);
    }

    boolean isDone(){
        return state instanceof DoneState;
    }

    int numberOfRoundsPlayed(){
        return roundsPlayed.size();
    }

    public Optional<HandResult> getResult() {
        return Optional.ofNullable(result);
    }

    void setResult(HandResult result) {
        this.result = result;
    }

    public boolean hasWinner(){
        return result != null;
    }

    void setScore(HandScore score) {
        this.score = score;
    }

    Player getFirstToPlay() {
        return firstToPlay;
    }

    Player getLastToPlay() {
        return lastToPlay;
    }

    HandScore getScore() {
        return score;
    }

    void setLastBetRaiser(Player lastBetRaiser) {
        this.lastBetRaiser = lastBetRaiser;
    }

    List<Card> getOpenCards() {
        return openCards;
    }

    public Intel getIntelOLD(){
        return Intel.ofHand(this, null);
    }

    void setState(HandState state) {
        this.state = state;
    }

    void setCurrentPlayer(Player currentPlayer) {
        this.currentPlayer = currentPlayer;
    }

    public Player getCurrentPlayer() {
        return currentPlayer;
    }

    Card getVira() {
        return vira;
    }

    Player getLastBetRaiser() {
        return lastBetRaiser;
    }

    public EnumSet<PossibleAction> getPossibleActions() {
        return possibleActions;
    }

    void setPossibleActions(EnumSet<PossibleAction> actions){
        this.possibleActions = EnumSet.copyOf(actions);
    }
}