import React, { useState } from 'react';
import './styles.css';

export default function FeedbackWidget() {
  const [isOpen, setIsOpen] = useState(false);
  const [feedback, setFeedback] = useState('');
  const [submitted, setSubmitted] = useState(false);

  const handleSubmit = (e) => {
    e.preventDefault();
    // In a real implementation, you would send this to an analytics service
    console.log('Feedback submitted:', feedback);
    setSubmitted(true);
    setTimeout(() => {
      setIsOpen(false);
      setSubmitted(false);
      setFeedback('');
    }, 2000);
  };

  return (
    <div className="feedback-widget">
      {!isOpen && (
        <button 
          className="feedback-button"
          onClick={() => setIsOpen(true)}
          aria-label="Give feedback"
        >
          💬 Feedback
        </button>
      )}
      
      {isOpen && (
        <div className="feedback-modal">
          <div className="feedback-header">
            <h3>Send us feedback</h3>
            <button 
              className="feedback-close"
              onClick={() => setIsOpen(false)}
              aria-label="Close feedback"
            >
              ✕
            </button>
          </div>
          
          {!submitted ? (
            <form onSubmit={handleSubmit}>
              <textarea
                value={feedback}
                onChange={(e) => setFeedback(e.target.value)}
                placeholder="How can we improve the documentation?"
                rows="4"
                required
              />
              <div className="feedback-actions">
                <button type="submit" className="feedback-submit">
                  Send Feedback
                </button>
              </div>
            </form>
          ) : (
            <div className="feedback-success">
              <span>✓</span>
              <p>Thank you for your feedback!</p>
            </div>
          )}
        </div>
      )}
    </div>
  );
}