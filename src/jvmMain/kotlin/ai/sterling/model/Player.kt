package ai.sterling.model

data class Player(val mancala: Int) {
    fun addToMancala(stones: Int): Player = copy(mancala = mancala + stones)
}
